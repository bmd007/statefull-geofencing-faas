package statefull.geofencing.faas.realtime.fencing.streamprocessing;

import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.utils.Bytes;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.state.KeyValueStore;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.locationtech.jts.io.WKTReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.support.serializer.JsonSerde;
import statefull.geofencing.faas.common.domain.Mover;
import statefull.geofencing.faas.realtime.fencing.CustomSerdes;
import statefull.geofencing.faas.realtime.fencing.config.Stores;
import statefull.geofencing.faas.realtime.fencing.config.Topics;
import statefull.geofencing.faas.realtime.fencing.domain.Fence;
import statefull.geofencing.faas.realtime.fencing.domain.FenceIntersectionStatus;

import javax.annotation.PostConstruct;
import java.util.function.BiFunction;

@Configuration
public class KStreamAndKTableDefinitions {

    public final static GeometryFactory GEOMETRY_FACTORY = new GeometryFactory(new PrecisionModel(PrecisionModel.maximumPreciseValue), 4326);
    public final static WKTReader wktReader = new WKTReader(GEOMETRY_FACTORY);
    private static final Logger LOGGER = LoggerFactory.getLogger(KStreamAndKTableDefinitions.class);
    private static final Consumed<String, Mover> MOVER_CONSUMED = Consumed.with(Serdes.String(), CustomSerdes.MOVER_JSON_SERDE);
    private static final Consumed<String, String> WKT_CONSUMED = Consumed.with(Serdes.String(), Serdes.String());
    private static final Materialized<String, Fence, KeyValueStore<Bytes, byte[]>> FENCE_KTABLE = Materialized
            .<String, Fence, KeyValueStore<Bytes, byte[]>>as(Stores.FENCE_STATE_STORE)
            .withKeySerde(Serdes.String())
            .withValueSerde(new JsonSerde<Fence>(Fence.class));

    private final StreamsBuilder streamsBuilder;
    BiFunction<Mover, Fence, KeyValue<String, FenceIntersectionStatus>> moverFenceIntersectionChecker = (mover, fence) -> {
        try {
            var point = GEOMETRY_FACTORY.createPoint(new Coordinate(mover.getLastLocation().getLatitude(),
                    mover.getLastLocation().getLongitude()));
            var fenceGeometry = wktReader.read(fence.getWkt());
            var intersects = fenceGeometry.intersects(point);
            var intersectionStatus = FenceIntersectionStatus.define(intersects, mover.getId());
            return KeyValue.pair(mover.getId(), intersectionStatus);
        } catch (Exception e) {
            LOGGER.error("error while intersecting {} with fence {}", mover, fence, e);
            return KeyValue.pair(mover.getId(), null);
        }
    };

    public KStreamAndKTableDefinitions(StreamsBuilder streamsBuilder) {
        this.streamsBuilder = streamsBuilder;
    }

    @PostConstruct
    public void configureStores() {
        var moversFenceKTable = streamsBuilder.stream(Topics.FENCE_EVENT_LOG, WKT_CONSUMED)
                .filterNot((key, value) -> key == null || key.isEmpty() || key.isBlank())
                .filterNot((key, value) -> value == null || value.isEmpty() || value.isBlank())
                .groupByKey()
                .aggregate(Fence::defineEmpty,
                        (moverId, newWkt, currentFence) -> Fence.define(newWkt, moverId),
                        FENCE_KTABLE);

        streamsBuilder.stream(Topics.MOVER_UPDATES_TOPIC, MOVER_CONSUMED)
                .filterNot((key, value) -> key == null || key.isEmpty() || key.isBlank())
                .filterNot((key, value) -> value.isNotDefined())
                .join(moversFenceKTable, moverFenceIntersectionChecker::apply)
                .foreach((moverId, intersects) -> System.out.println(intersects));//todo integrate alarming function
        // here.
    }
}

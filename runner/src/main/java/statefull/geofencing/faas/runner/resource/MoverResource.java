package statefull.geofencing.faas.runner.resource;

import org.locationtech.jts.geom.Polygon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;
import statefull.geofencing.faas.common.domain.Mover;
import statefull.geofencing.faas.common.repository.MoverJdbcRepository;
import statefull.geofencing.faas.runner.dto.CoordinateDto;
import statefull.geofencing.faas.runner.dto.MoverDto;
import statefull.geofencing.faas.runner.dto.MoversDto;

import javax.validation.Valid;
import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/movers")
public class MoverResource {

    private static final Logger LOGGER = LoggerFactory.getLogger(MoverResource.class);
    private final MoverJdbcRepository repository;
    private BiFunction<MoverJdbcRepository, Polygon, List<Mover>> polygonalGeoFencingFunction;
    private BiFunction<Double, Double, Polygon> wrapLocationByPolygonFunction;

    public MoverResource(MoverJdbcRepository repository,
                         BiFunction<MoverJdbcRepository, Polygon, List<Mover>> polygonalGeoFencingFunction,
                         BiFunction<Double, Double, Polygon> wrapLocationByPolygonFunction) {
        this.repository = repository;
        this.polygonalGeoFencingFunction = polygonalGeoFencingFunction;
        this.wrapLocationByPolygonFunction = wrapLocationByPolygonFunction;
    }

    @GetMapping("/{id}")
    public MoverDto get(@PathVariable("id") String id) {
        return map(repository.get(id));
    }

    @GetMapping("/box")
    public MoversDto queryBox(@RequestParam(required = true) double latitude,
                              @RequestParam(required = true) double longitude,
                              @RequestParam(required = false) Long maxAge,
                              @RequestParam(required = false) Set<Boolean> availability) {
        return queryPolygon(wrapLocationByPolygonFunction.apply(latitude, longitude), maxAge);
    }

    @PostMapping("/polygon")
    public MoversDto queryPolygon(@Valid @RequestBody Polygon polygon, @RequestParam(required = false) Long maxAge) {
        LOGGER.debug("Executing query. MaxAge: {}, Polygon: {}", maxAge, polygon);
        var results = polygonalGeoFencingFunction.apply(repository, polygon)
                .stream()
                .map(this::map)
                .collect(Collectors.toList());
        return new MoversDto(results);
    }


    private MoverDto map(Mover v) {
        return new MoverDto(v.getId(), new CoordinateDto(v.getLastLocation().getLatitude(), v.getLastLocation().getLongitude()));
    }
}

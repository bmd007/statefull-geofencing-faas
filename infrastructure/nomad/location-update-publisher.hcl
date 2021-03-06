job "location-update-publisher" {
  datacenters = ["dc1"]
  type =        "service"

  meta {
    version = "IMAGE_NAME"
  }

  update {
    # The update stanza specifies the group's update strategy.
    max_parallel =     1
    health_check =     "checks"
    min_healthy_time = "30s"
  }


  group "location-update-publisher" {
    count = 4

    restart {
      delay = "15s"
      mode =  "delay"
    }

    network {
      mode = "host"
      port "http" {}
      port "management" {}
    }

    task "location-update-publisher" {
      driver = "docker"
      # Configuration is specific to each driver.
      config {
        image =      "bmd007/location-update-publisher"
        network_mode = "host"
        force_pull = true
		ports = ["http", "management"]
      }

      service {
        name = "location-update-publisher"
        port = "http"
        check {
          type =     "http"
          port =     "management"
          interval = "10s"
          timeout =  "5s"
          path =     "/health"
          check_restart {
            limit =           4
            grace =           "15m"
            ignore_warnings = true
          }
        }
        connect { native = true }
      }

      service {
        name = "location-update-publisher-management"
        tags = ["management"]
        port = "management"
        check {
          type =     "http"
          interval = "10s"
          timeout =  "5s"
          path =     "/health"
        }
        connect { native = true }
      }
      env {
        SERVER_PORT= "${NOMAD_PORT_http}"
        MANAGEMENT_SERVER_PORT= "${NOMAD_PORT_management}"
        KAFKA_TOPIC_CONFIG_EVENT= "12:3"
        KAFKA_TOPIC_CONFIG_CHANGELOG= "12:3"
        SPRING_KAFKA_BOOTSTRAP_SERVERS= "${NOMAD_IP_http}:9092"
        SPRING_PROFILES_ACTIVE =                                  "nomad"
        SPRING_CLOUD_CONSUL_HOST =                                "localhost"
        SPRING_CLOUD_SERVICE_REGISTRY_AUTO_REGISTRATION_ENABLED = "false"
      }
      resources {
        cpu =    400
        memory = 700
      }
    }
  }
}

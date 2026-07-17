# Local infrastructure-as-code for the copilot demo, targeting the Docker provider.
#
# This declares a Terraform-managed copy of the demo app. Its purpose is to give the infra/drift
# agent something real to reason about: when someone changes the running container out-of-band
# (e.g. `docker container update` or an env edit), a refresh-only `terraform plan` reports the
# drift, which terraform-mcp's plan_diff surfaces and the infra agent explains.
#
# The same module structure is reused for AWS later (Phase 8) by swapping the provider.

terraform {
  required_version = ">= 1.6"
  required_providers {
    docker = {
      source  = "kreuzwerker/docker"
      version = "~> 3.0"
    }
  }
}

provider "docker" {}

variable "image" {
  description = "Image for the Terraform-managed demo app (build it first: docker compose build demo-app)."
  type        = string
  default     = "agentic-sre/demo-app:latest"
}

variable "log_level" {
  description = "Declared log level for the managed container (drift target)."
  type        = string
  default     = "INFO"
}

resource "docker_container" "demo_app_managed" {
  name  = "demo-app-managed"
  image = var.image

  # The declared desired state. Editing these on the live container outside Terraform
  # (e.g. recreating it with a different LOG_LEVEL) is what the infra agent detects as drift.
  env = [
    "LOG_LEVEL=${var.log_level}",
    "MANAGED_BY=terraform"
  ]

  labels {
    label = "managed-by"
    value = "terraform"
  }

  ports {
    internal = 8080
    external = 8081
  }

  restart = "unless-stopped"
}

output "managed_container_name" {
  value = docker_container.demo_app_managed.name
}

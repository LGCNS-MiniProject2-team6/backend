"""Update only the app container image in one dev Deployment patch."""
import json
import re
import sys
from pathlib import Path


def update(root, service, image):
    if service not in {
        "discovery-server", "config-server", "api-gateway",
        "user-service", "ai-service", "blog-service",
    }:
        raise ValueError("Unknown service")
    repository = "recu-" + service.replace("-", "")
    if not re.fullmatch(rf"docker.io/junwooqkr5302/{repository}:[0-9a-f]{{40}}", image):
        raise ValueError("Expected the published image with a full commit SHA")
    path = Path(root) / "overlays/dev-apps/images" / f"{service}.json"
    data = json.loads(path.read_text(encoding="utf-8"))
    if data.get("kind") != "Deployment" or data["metadata"]["name"] != service:
        raise ValueError("Unexpected Deployment patch")
    containers = data["spec"]["template"]["spec"]["containers"]
    matches = [c for c in containers if c["name"] == "app"]
    if len(matches) != 1:
        raise ValueError("Expected exactly one app container")
    matches[0]["image"] = image
    path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    update(*sys.argv[1:])

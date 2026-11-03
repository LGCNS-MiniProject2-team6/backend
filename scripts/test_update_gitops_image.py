import importlib.util
import json
import tempfile
import unittest
from pathlib import Path

spec = importlib.util.spec_from_file_location("updater", Path(__file__).with_name("update-gitops-image.py"))
updater = importlib.util.module_from_spec(spec)
spec.loader.exec_module(updater)


class UpdateImageTest(unittest.TestCase):
    def test_only_target_image_changes_and_invalid_input_does_not_write(self):
        with tempfile.TemporaryDirectory() as root:
            path = Path(root) / "overlays/dev-apps/images/user-service.json"
            path.parent.mkdir(parents=True)
            original = {"kind": "Deployment", "metadata": {"name": "user-service"},
                        "spec": {"template": {"spec": {"containers": [
                            {"name": "app", "image": "old"},
                            {"name": "sidecar", "image": "keep"}]}}}}
            path.write_text(json.dumps(original), encoding="utf-8")
            image = "docker.io/junwooqkr5302/recu-userservice:" + "a" * 40
            updater.update(root, "user-service", image)
            original["spec"]["template"]["spec"]["containers"][0]["image"] = image
            self.assertEqual(json.loads(path.read_text()), original)
            before = path.read_bytes()
            for service, value in [("../prod", image), ("user-service", "wrong:latest")]:
                with self.assertRaises(ValueError):
                    updater.update(root, service, value)
            self.assertEqual(path.read_bytes(), before)


if __name__ == "__main__":
    unittest.main()

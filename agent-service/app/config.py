from dataclasses import dataclass, field
import os
from pathlib import Path


@dataclass(frozen=True)
class Settings:
    java_api_url: str
    service_key: str = field(repr=False)
    api_key: str = field(repr=False)
    base_url: str
    model: str
    checkpoint_path: Path

    @property
    def model_configured(self) -> bool:
        return bool(self.api_key and self.base_url and self.model)

    @classmethod
    def from_env(cls) -> "Settings":
        return cls(
            java_api_url=os.getenv("JAVA_API_URL", "http://127.0.0.1:8080").rstrip("/"),
            service_key=os.getenv("INTERNAL_SERVICE_KEY", ""),
            api_key=os.getenv("OPENAI_API_KEY", ""),
            base_url=os.getenv("OPENAI_BASE_URL", ""),
            model=os.getenv("OPENAI_MODEL", ""),
            checkpoint_path=Path(os.getenv("CHECKPOINT_PATH", "./data/checkpoints.sqlite")),
        )

from pydantic_settings import BaseSettings, SettingsConfigDict

class ZmqSettings(BaseSettings):
    """Configuration settings for the ZMQ client."""
    base_url: str = "http://localhost:8080"
    timeout: float = 10.0
    log_level: str = "INFO"

    model_config = SettingsConfigDict(
        env_prefix="ZMQ_CLIENT_",
        env_file=".env",
        extra="ignore"
    )

settings = ZmqSettings()

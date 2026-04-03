class ZmqClientError(Exception):
    """Base exception for ZMQ client."""
    pass

class ZmqApiError(ZmqClientError):
    """Raised when the API returns an error response."""
    def __init__(self, message: str, status_code: int = None, details: dict = None):
        super().__init__(message)
        self.status_code = status_code
        self.details = details

class ZmqValidationError(ZmqClientError):
    """Raised when request validation fails."""
    pass

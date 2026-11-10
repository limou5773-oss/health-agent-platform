"""Sleep health agent service."""

import os

# Also restrict the checkpointer's internal metadata serializer to safe types.
os.environ["LANGGRAPH_STRICT_MSGPACK"] = "true"

import json

from langchain_core.messages import AIMessage, BaseMessage, HumanMessage
from redis.asyncio import Redis


class RedisChatHistory:
    def __init__(
        self,
        redis: Redis,
        history_turns: int,
        ttl_seconds: int,
    ) -> None:
        self.redis = redis
        self.message_limit = history_turns * 2
        self.ttl_seconds = ttl_seconds

    @staticmethod
    def _key(session_id: str) -> str:
        return f"skytrace:chat:{session_id}"

    async def get_messages(self, session_id: str) -> list[BaseMessage]:
        values = await self.redis.lrange(
            self._key(session_id),
            -self.message_limit,
            -1,
        )

        messages: list[BaseMessage] = []
        for value in values:
            item = json.loads(value)
            if item["role"] == "user":
                messages.append(HumanMessage(content=item["content"]))
            elif item["role"] == "assistant":
                messages.append(AIMessage(content=item["content"]))

        return messages

    async def append_exchange(
        self,
        session_id: str,
        user_message: str,
        assistant_message: str,
    ) -> None:
        key = self._key(session_id)
        user_value = json.dumps(
            {"role": "user", "content": user_message},
            ensure_ascii=False,
        )
        assistant_value = json.dumps(
            {"role": "assistant", "content": assistant_message},
            ensure_ascii=False,
        )

        async with self.redis.pipeline(transaction=True) as pipeline:
            pipeline.rpush(key, user_value, assistant_value)
            pipeline.ltrim(key, -self.message_limit, -1)
            pipeline.expire(key, self.ttl_seconds)
            await pipeline.execute()

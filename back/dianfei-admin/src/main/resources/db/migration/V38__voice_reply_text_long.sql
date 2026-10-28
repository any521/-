-- F7 语音碳管家升级 AI+RAG Markdown 回答：VARCHAR(500) 会截断长 Markdown 报 1406，放宽为 TEXT
ALTER TABLE biz_voice_session MODIFY user_text  TEXT COMMENT 'ASR 转写/用户输入文本';
ALTER TABLE biz_voice_session MODIFY slots_json TEXT COMMENT '槽位快照 {category, appoint_time, address}';
ALTER TABLE biz_voice_session MODIFY reply_text TEXT COMMENT 'TTS 播报/回复文本（Markdown）';

-- V95__add_image_url_to_chat_messages.sql

ALTER TABLE chat_messages
ADD (image_url VARCHAR2(2048));

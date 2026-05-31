package com.lab9.common;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class MessageProtocol {

    public static class Message {
        private String command;
        private String username;
        private String target;
        private String content;
        private String response;
        private List<String> items = new ArrayList<>();

        public Message() {}

        private Message(Builder builder) {
            this.command = builder.command;
            this.username = builder.username;
            this.target = builder.target;
            this.content = builder.content;
            this.response = builder.response;
            this.items = builder.items;
        }

        public String getCommand() { return command; }
        public String getUsername() { return username; }
        public String getTarget() { return target; }
        public String getContent() { return content; }
        public String getResponse() { return response; }
        public List<String> getItemsList() { return items; }

        public void writeDelimitedTo(OutputStream out) throws IOException {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);

            dos.writeUTF(command != null ? command : "");
            dos.writeUTF(username != null ? username : "");
            dos.writeUTF(target != null ? target : "");
            dos.writeUTF(content != null ? content : "");
            dos.writeUTF(response != null ? response : "");
            dos.writeInt(items.size());
            for (String item : items) {
                dos.writeUTF(item);
            }

            byte[] data = baos.toByteArray();
            out.write((data.length >> 24) & 0xFF);
            out.write((data.length >> 16) & 0xFF);
            out.write((data.length >> 8) & 0xFF);
            out.write(data.length & 0xFF);
            out.write(data);
        }

        public static Message parseDelimitedFrom(InputStream in) throws IOException {
            int len = (in.read() << 24) | (in.read() << 16) | (in.read() << 8) | in.read();
            if (len < 0) return null;

            byte[] data = new byte[len];
            int read = 0;
            while (read < len) {
                int r = in.read(data, read, len - read);
                if (r < 0) return null;
                read += r;
            }

            DataInputStream dis = new DataInputStream(new ByteArrayInputStream(data));
            Message msg = new Message();
            msg.command = dis.readUTF();
            msg.username = dis.readUTF();
            msg.target = dis.readUTF();
            msg.content = dis.readUTF();
            msg.response = dis.readUTF();
            int size = dis.readInt();
            msg.items = new ArrayList<>();
            for (int i = 0; i < size; i++) {
                msg.items.add(dis.readUTF());
            }
            return msg;
        }

        public static Builder newBuilder() {
            return new Builder();
        }

        public static class Builder {
            private String command;
            private String username;
            private String target;
            private String content;
            private String response;
            private List<String> items = new ArrayList<>();

            public Builder setCommand(String cmd) { this.command = cmd; return this; }
            public Builder setUsername(String name) { this.username = name; return this; }
            public Builder setTarget(String t) { this.target = t; return this; }
            public Builder setContent(String c) { this.content = c; return this; }
            public Builder setResponse(String r) { this.response = r; return this; }
            public Builder addAllItems(List<String> list) {
                this.items = new ArrayList<>(list);
                return this;
            }

            public Message build() {
                return new Message(this);
            }
        }
    }
}
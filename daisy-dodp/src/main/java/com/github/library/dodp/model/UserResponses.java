package com.github.library.dodp.model;

import com.github.library.dodp.util.XmlUtil;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.github.library.dodp.DodpNamespaces.DAISY_ONLINE;

/**
 * User responses to the questions issued by the service, sent as the argument
 * to {@code getQuestions} (Dynamic Menus). Built and serialized by the client.
 */
public final class UserResponses {

    private final List<UserResponse> responses;

    public UserResponses(List<UserResponse> responses) {
        this.responses = responses == null ? Collections.emptyList() : responses;
    }

    public List<UserResponse> getResponses() {
        return responses;
    }

    public Element toElement(Document document) {
        Element root = document.createElementNS(DAISY_ONLINE, "userResponses");
        for (UserResponse response : responses) {
            Element element = XmlUtil.appendChild(root, DAISY_ONLINE, "userResponse");
            element.setAttribute("questionID", response.questionId);
            if (response.value != null) {
                element.setAttribute("value", response.value);
            }
            if (response.data != null) {
                XmlUtil.appendChildText(element, DAISY_ONLINE, "data", response.data);
            }
        }
        return root;
    }

    public static UserResponses of(String questionId, String value) {
        return new UserResponses(Collections.singletonList(new UserResponse(questionId, value, null)));
    }

    public static final class UserResponse {

        private final String questionId;
        private final String value;
        private final String data;

        public UserResponse(String questionId, String value, String data) {
            this.questionId = questionId;
            this.value = value;
            this.data = data;
        }

        public String getQuestionId() {
            return questionId;
        }

        public String getValue() {
            return value;
        }

        public String getData() {
            return data;
        }
    }

    public static final class Builder {

        private final List<UserResponse> responses = new ArrayList<>();

        public Builder add(String questionId, String value) {
            responses.add(new UserResponse(questionId, value, null));
            return this;
        }

        public Builder add(String questionId, String value, String data) {
            responses.add(new UserResponse(questionId, value, data));
            return this;
        }

        public UserResponses build() {
            return new UserResponses(new ArrayList<>(responses));
        }
    }
}

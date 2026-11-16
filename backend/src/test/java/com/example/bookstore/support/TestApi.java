package com.example.bookstore.support;

import java.util.List;
import java.util.Map;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

/** Thin HTTP helper that never throws on 4xx/5xx so tests can assert on error responses. */
public class TestApi {

    private static final ParameterizedTypeReference<Map<String, Object>> MAP = new ParameterizedTypeReference<>() {
    };
    private static final ParameterizedTypeReference<List<Map<String, Object>>> LIST =
            new ParameterizedTypeReference<>() {
            };

    private final RestClient client;

    public TestApi(int port) {
        this.client = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .defaultStatusHandler(HttpStatusCode::isError, (request, response) -> {
                })
                .build();
    }

    public ResponseEntity<Map<String, Object>> get(String path) {
        return client.get().uri(path).retrieve().toEntity(MAP);
    }

    public ResponseEntity<List<Map<String, Object>>> getList(String path) {
        return client.get().uri(path).retrieve().toEntity(LIST);
    }

    public ResponseEntity<Map<String, Object>> post(String path, Object body) {
        RestClient.RequestBodySpec spec = client.post().uri(path).contentType(MediaType.APPLICATION_JSON);
        if (body != null) {
            spec.body(body);
        }
        return spec.retrieve().toEntity(MAP);
    }

    public ResponseEntity<Map<String, Object>> put(String path, Object body) {
        return client.put().uri(path).contentType(MediaType.APPLICATION_JSON).body(body).retrieve().toEntity(MAP);
    }

    public ResponseEntity<Map<String, Object>> patch(String path, Object body) {
        return client.patch().uri(path).contentType(MediaType.APPLICATION_JSON).body(body).retrieve().toEntity(MAP);
    }

    public ResponseEntity<Map<String, Object>> delete(String path) {
        return client.delete().uri(path).retrieve().toEntity(MAP);
    }

    /** Posts an application/x-www-form-urlencoded body and returns the plain-text response. */
    public ResponseEntity<String> postForm(String path, Map<String, String> form) {
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        form.forEach(body::add);
        return client.post().uri(path).contentType(MediaType.APPLICATION_FORM_URLENCODED).body(body)
                .retrieve().toEntity(String.class);
    }
}

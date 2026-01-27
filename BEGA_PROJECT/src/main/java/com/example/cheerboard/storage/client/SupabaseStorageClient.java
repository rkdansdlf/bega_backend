package com.example.cheerboard.storage.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Slf4j
public class SupabaseStorageClient {

        private final WebClient webClient;
        private final String supabaseUrl;

        public SupabaseStorageClient(String supabaseUrl, String supabaseKey) {
                this.supabaseUrl = supabaseUrl;
                this.webClient = WebClient.builder()
                                .baseUrl(supabaseUrl)
                                .defaultHeader("Authorization", "Bearer " + supabaseKey)
                                .defaultHeader("apikey", supabaseKey)
                                .build();
        }

        public Mono<String> upload(byte[] bytes, String bucket, String path, String contentType) {
                // Supabase Storage API: POST /storage/v1/object/{bucket}/{path} (upsert=true to
                // overwrite)
                // If path contains slashes, they are part of the URL path

                return webClient.post()
                                .uri(uriBuilder -> uriBuilder
                                                .path("/storage/v1/object/{bucket}/{path}")
                                                .build(bucket, path))
                                .contentType(MediaType.parseMediaType(contentType))
                                .body(BodyInserters.fromValue(bytes))
                                .retrieve()
                                .bodyToMono(String.class)
                                .map(response -> bucket + "/" + path) // Return full path on success
                                .doOnError(e -> log.error("Supabase upload failed: {}", e.getMessage()));
        }

        public Mono<Void> delete(String bucket, String path) {
                return webClient.delete()
                                .uri(uriBuilder -> uriBuilder
                                                .path("/storage/v1/object/{bucket}/{path}")
                                                .build(bucket, path))
                                .retrieve()
                                .bodyToMono(Void.class)
                                .doOnError(e -> log.error("Supabase delete failed: {}", e.getMessage()));
        }

        public String getPublicUrl(String bucket, String path) {
                // https://[project].supabase.co/storage/v1/object/public/[bucket]/[path]
                return String.format("%s/storage/v1/object/public/%s/%s", supabaseUrl, bucket, path);
        }
}

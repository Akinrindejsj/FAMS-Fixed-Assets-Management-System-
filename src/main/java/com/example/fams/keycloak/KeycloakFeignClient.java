package com.example.fams.keycloak;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@FeignClient(name = "KeycloakFeignClient", url = "${keycloak.base-url}")
public interface KeycloakFeignClient {

    // 🔹 Token retrieval (form-url-encoded)
    @PostMapping(
            path = "/realms/attorneyAI/protocol/openid-connect/token",
            consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE
    )
    Map<String, Object> getAccessToken(@RequestParam Map<String, ?> paramMap);

    // 🔹 Users
    @GetMapping("/admin/realms/attorneyAI/users")
    List<Map<String, Object>> getUsers(
            @RequestHeader("Authorization") String authorizationHeader
    );

    @GetMapping("/admin/realms/attorneyAI/users")
    ResponseEntity<List<Map<String, Object>>> getUserByEmail(
            @RequestHeader("Authorization") String authorizationHeader,
            @RequestParam("email") String email
    );

    @GetMapping("/admin/realms/attorneyAI/users/{userId}")
    Map<String, Object> getUser(
            @RequestHeader("Authorization") String authorizationHeader,
            @PathVariable String userId
    );

    @PostMapping("/admin/realms/attorneyAI/users")
    ResponseEntity<Void> createUser(
            @RequestHeader("Authorization") String authorizationHeader,
            @RequestBody Map<String, Object> user
    );

    @PutMapping("/admin/realms/attorneyAI/users/{userId}")
    ResponseEntity<Void> updateUser(
            @RequestHeader("Authorization") String authorizationHeader,
            @PathVariable String userId,
            @RequestBody Map<String, Object> body
    );

    @DeleteMapping("/admin/realms/attorneyAI/users/{userId}")
    ResponseEntity<Void> deleteUser(
            @RequestHeader("Authorization") String authorizationHeader,
            @PathVariable String userId
    );

    // 🔹 Groups
    @GetMapping("/admin/realms/attorneyAI/groups")
    List<Map<String, Object>> getRealmGroups(
            @RequestHeader("Authorization") String authorizationHeader
    );

    @PostMapping("/admin/realms/attorneyAI/groups")
    ResponseEntity<Void> createGroup(
            @RequestHeader("Authorization") String authorizationHeader,
            @RequestBody Map<String, ?> group
    );

    @PutMapping("/admin/realms/attorneyAI/groups/{groupId}")
    ResponseEntity<Void> updateGroup(
            @RequestHeader("Authorization") String authorizationHeader,
            @PathVariable String groupId,
            @RequestBody Map<String, Object> groupBody
    );

    @DeleteMapping("/admin/realms/attorneyAI/groups/{groupId}")
    ResponseEntity<Void> deleteGroup(
            @RequestHeader("Authorization") String authorizationHeader,
            @PathVariable String groupId
    );

    @GetMapping("/admin/realms/attorneyAI/groups/{groupId}/members")
    List<Map<String, Object>> getAllUserInGroup(
            @RequestHeader("Authorization") String authorizationHeader,
            @PathVariable("groupId") String groupId
    );

    // 🔹 User-group relations
    @PutMapping("/admin/realms/attorneyAI/users/{userId}/groups/{groupId}")
    ResponseEntity<Void> addUserToGroup(
            @RequestHeader("Authorization") String authorizationHeader,
            @PathVariable String userId,
            @PathVariable String groupId
    );

    @GetMapping("/admin/realms/attorneyAI/users/{id}/groups")
    List<Map<String, Object>> getUserGroups(
            @RequestHeader("Authorization") String authorizationHeader,
            @PathVariable("id") String userId
    );
}

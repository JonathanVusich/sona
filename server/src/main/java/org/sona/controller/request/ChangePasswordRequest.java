package org.sona.controller.request;

public record ChangePasswordRequest(String currentPassword, String newPassword) {
}

package com.tejesh.timetable.dto;

import java.util.Objects;

/**
 * <h2>UserRegistrationDto – User Registration Form DTO</h2>
 *
 * <p>Transfers and encapsulates student registration input data between the
 * {@code register.html} Thymeleaf template and {@link com.tejesh.timetable.controller.AuthController}.
 */
public class UserRegistrationDto {

    private String username;
    private String password;
    private String confirmPassword;
    private String examTarget;

    public UserRegistrationDto() {}

    public UserRegistrationDto(String username, String password, String confirmPassword, String examTarget) {
        this.username = username;
        this.password = password;
        this.confirmPassword = confirmPassword;
        this.examTarget = examTarget;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getConfirmPassword() {
        return confirmPassword;
    }

    public void setConfirmPassword(String confirmPassword) {
        this.confirmPassword = confirmPassword;
    }

    public String getExamTarget() {
        return examTarget;
    }

    public void setExamTarget(String examTarget) {
        this.examTarget = examTarget;
    }

    @Override
    public String toString() {
        return "UserRegistrationDto{" +
                "username='" + username + '\'' +
                ", examTarget='" + examTarget + '\'' +
                '}';
    }
}

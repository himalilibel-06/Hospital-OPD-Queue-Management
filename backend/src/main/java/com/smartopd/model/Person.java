package com.smartopd.model;

import com.smartopd.exception.ValidationException;

/**
 * Person - Abstract base class encapsulating common human demographics.
 * 
 * ============================================================================
 * OOP Concept: Abstraction, Inheritance & Strict Encapsulation
 * - Defines common attributes (id, name, age, gender, phone) across all hospital users.
 * - Enforces business validation rules (10-digit mobile phone, age limits) via setters.
 * ============================================================================
 */
public abstract class Person {
    private String id;
    private String name;
    private int age;
    private String gender;
    private String phone;

    public Person(String id, String name, int age, String gender, String phone) {
        setId(id);
        setName(name);
        setAge(age);
        setGender(gender);
        setPhone(phone);
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        if (id == null || id.trim().isEmpty()) {
            throw new ValidationException("ID is required.");
        }
        this.id = id.trim();
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new ValidationException("Patient full name is required.");
        }
        this.name = name.trim();
    }

    public int getAge() {
        return age;
    }

    public void setAge(int age) {
        if (age <= 0 || age > 130) {
            throw new ValidationException("Please enter a valid patient age.");
        }
        this.age = age;
    }

    public String getGender() {
        return gender;
    }

    public void setGender(String gender) {
        if (gender == null || gender.trim().isEmpty()) {
            this.gender = "Other";
        } else {
            this.gender = gender.trim();
        }
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        if (phone == null) {
            throw new ValidationException("Please enter a valid 10-digit mobile number.");
        }
        String cleanPhone = phone.trim().replaceAll("[-+\\s]", "");
        if (!cleanPhone.matches("^\\d{10}$")) {
            throw new ValidationException("Please enter a valid 10-digit mobile number.");
        }
        this.phone = cleanPhone;
    }
}

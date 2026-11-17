package com.example.dz.server.users.entity

/** Stored by name; V11 pins the allowed names with a check constraint. */
enum class Gender { FEMALE, MALE, PREFER_NOT_TO_SAY }

/** Whether [User.speciality] is a job or a course of study. */
enum class SpecialityKind { WORK, STUDY }

/**
 * Who may see the profile as a whole. Shelves keep their own setting.
 *
 * Only stored here so far: the social endpoints do not read it yet.
 */
enum class ProfileVisibility { OPEN, PRIVATE }

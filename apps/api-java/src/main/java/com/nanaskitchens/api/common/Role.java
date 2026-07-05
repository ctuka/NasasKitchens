package com.nanaskitchens.api.common;

/** Lowercase constants on purpose: they must match the Prisma "Role" enum values in the shared DB. */
public enum Role {
    buyer,
    seller,
    inspector,
    admin
}

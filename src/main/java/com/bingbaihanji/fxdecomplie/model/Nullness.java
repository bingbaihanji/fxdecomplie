package com.bingbaihanji.fxdecomplie.model;


/**
 * Nullability state.
 *
 * @author Matt Coley
 */
public enum Nullness {
    UNKNOWN, NULL, NOT_NULL;

    /**
     * @param other
     * 		Other value to merge with.
     *
     * @return Common nullability state.
     */

    public Nullness mergeWith(Nullness other) {
        if (this != other) {
            return UNKNOWN;
        }
        return this;
    }
}

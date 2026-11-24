package dev.specbuilder.domain;

/**
 * What a payload is for. Both can be used anywhere; the role sorts the object inventory from the
 * bodies built out of it.
 */
public enum PayloadRole {
    /** A reusable building block, such as Money, Address or Merchant, kept in the object inventory. */
    OBJECT,
    /** A request, response, message or working body, assembled from fields and objects. */
    STRUCTURE
}

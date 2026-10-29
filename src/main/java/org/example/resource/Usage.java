package org.example.resource;

/** An amount of a resource consumed, e.g. 10 kWh of energy. */
public record Usage(Resource resource, int amount) {}

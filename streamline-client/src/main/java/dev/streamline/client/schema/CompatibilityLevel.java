package dev.streamline.client.schema;

/**
 * Schema compatibility levels enforced by the Streamline schema registry.
 *
 * <p>Compatibility levels control which schema evolutions are permitted
 * when registering new versions of a subject.
 *
 * <ul>
 *   <li>{@link #BACKWARD} — new schema can read data written by the last schema version.</li>
 *   <li>{@link #FORWARD} — last schema version can read data written by the new schema.</li>
 *   <li>{@link #FULL} — both backward and forward compatible with the last version.</li>
 *   <li>{@link #NONE} — no compatibility checks are performed.</li>
 *   <li>{@link #BACKWARD_TRANSITIVE} — backward compatible with <em>all</em> prior versions.</li>
 *   <li>{@link #FORWARD_TRANSITIVE} — forward compatible with <em>all</em> prior versions.</li>
 *   <li>{@link #FULL_TRANSITIVE} — fully compatible with <em>all</em> prior versions.</li>
 * </ul>
 */
public enum CompatibilityLevel {

    BACKWARD,
    FORWARD,
    FULL,
    NONE,
    BACKWARD_TRANSITIVE,
    FORWARD_TRANSITIVE,
    FULL_TRANSITIVE
}

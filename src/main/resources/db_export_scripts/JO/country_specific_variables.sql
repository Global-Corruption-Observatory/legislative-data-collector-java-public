SELECT
    record_id,
    affecting_laws_size_total,
    affecting_laws_total_article_count,
    category,
    leg_status,
    related_total,
    related_regulation,
    related_directions,
    related_decisions,
    related_explanation
FROM jordan_spec_vars
ORDER BY record_id;

-- name: add_content
INSERT INTO content(id, created_time, origin_time, type, origin, parent_id, permalink, parsed, author, body)
VALUES({:id}, now(), {:origin_time}, {:type}, {:origin}, {:parent_id}, {:permalink}, {:parsed}, {:author}, {:body})
ON CONFLICT DO NOTHING


-- name: get_recursive_tree
WITH RECURSIVE tree AS (
  SELECT id, created_time, origin_time, type, origin, parent_id, permalink, parsed
  FROM content
  WHERE id = {:id}
  UNION ALL
    SELECT c.id, c.created_time, c.origin_time, c.type, c.origin, c.parent_id, c.permalink, c.parsed
    FROM content c
      JOIN tree
      ON c.id = tree.parent_id
) SELECT * FROM tree

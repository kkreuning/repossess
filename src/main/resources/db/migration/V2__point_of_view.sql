-- authors views
CREATE VIEW authors_n_commits
AS
  SELECT
    a.name AS author_name,
    COUNT(c.hash) AS n_commits
  FROM
    authors a,
    commits c
  WHERE
    a.name = c.author_name
  GROUP BY
    a.name
  ORDER BY
    n_commits DESC
;
-- TODO: Merge authors_n_commits into authors_contributions view
CREATE VIEW authors_contibutions
AS
  SELECT
      a.name AS author_name,
      SUM(s.lines_added) AS n_lines_added,
      SUM(s.lines_deleted) AS n_lines_deleted,
      SUM(s.lines_added + s.lines_deleted) AS n_lines_changed
  FROM
      authors a,
      commits c,
      snapshots s
  WHERE
      a.name = c.author_name AND
      s.commit_hash = c.hash AND
      s.changed = TRUE
  GROUP BY
    a.name
  ORDER BY
    a.name
;



-- files views
CREATE VIEW files_coupling
AS
  SELECT
    s1.file_path AS file1,
    s2.file_path AS file2,
    COUNT(s1.commit_hash) AS abs_coupling
  FROM
    snapshots s1 JOIN snapshots s2 ON s1.commit_hash = s2.commit_hash AND s1.file_path != s2.file_path
  WHERE
    s1.changed = TRUE AND s2.changed = TRUE
  GROUP BY
    s1.file_path, s2.file_path
  ORDER BY
    abs_coupling DESC
-- TODO: Deduplicate
;


CREATE VIEW files_n_authors
AS
  SELECT s.file_path, COUNT(DISTINCT(c.author_name)) AS n_authors
  FROM
    commits c,
    snapshots s
  WHERE
    c.hash = s.commit_hash AND
    s.changed = TRUE
  GROUP BY
    s.file_path
  ORDER BY
    n_authors DESC;
-- TODO: Merge files_n_authors and files_principal_authors into one files_ownership
CREATE VIEW files_principal_authors
AS
  SELECT
    file_path,
    author_name AS principal_author,
    n_commits
  FROM (
    SELECT
      f."path" AS file_path,
      c.author_name,
      COUNT(c.author_name) AS n_commits,
      ROW_NUMBER() OVER (PARTITION BY f."path" ORDER BY COUNT(c.author_name) DESC) AS "rank"
    FROM
      files f,
      snapshots s,
      commits c
    WHERE
      f."path" = s.file_path AND
      s.commit_hash = c.hash AND
      s.changed = TRUE
    GROUP BY
      c.author_name,
      f."path"
    ORDER BY
      f."path",
      "rank",
      c.author_name
  )
  WHERE "rank" = 1
;



-- commits views
CREATE VIEW commits_changes
AS
  SELECT
    c."date" AS commit_date,
      c.hash AS commit_hash,
      COUNT(f.path) AS n_files,
      SUM(s.lines_added) AS n_lines_added,
      SUM(s.lines_deleted) AS n_lines_deleted,
      SUM(s.total_lines) AS total_lines,
      SUM(s.code_lines) AS n_code_lines,
      SUM(s.blank_lines) AS n_blank_lines,
      SUM(s.comment_lines) AS n_comment_lines,
      SUM(s.complexity) AS abs_complexity,
      AVG(cast(SUM(s.complexity) AS REAL) / cast(COUNT(f.path) AS REAL)) OVER (ORDER BY c."date" ROWS BETWEEN 5 PRECEDING AND 5 FOLLOWING) AS avg_complexity
  FROM
      commits c,
      files f,
      snapshots s
  WHERE
    c.hash = s.commit_hash AND
    s.file_path = f.path
  GROUP BY
    c.hash
  ORDER BY
    c.date
;


CREATE VIEW commits_churn
AS
	SELECT
		c."date" AS commit_date,
		c.hash AS commit_hash,
		SUM(s.lines_added + s.lines_deleted) AS abs_churn,
		AVG(s.lines_added + s.lines_deleted) OVER (ORDER BY c."date" ROWS BETWEEN 2 PRECEDING AND 2 FOLLOWING) AS avg_churn
	FROM
		snapshots s,
		commits c
	WHERE
		s.commit_hash = c.hash AND
		s.lines_added + s.lines_deleted > 0
	GROUP BY
		c.hash
	ORDER BY
		c."date"
;

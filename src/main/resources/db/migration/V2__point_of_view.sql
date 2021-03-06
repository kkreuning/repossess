-- authors views
CREATE VIEW authors_contributions
AS
  SELECT
    n_commits.author_name,
    n_commits.n_commits,
    changes.n_lines_added,
    changes.n_lines_deleted,
    changes.n_lines_changed
  FROM
	  (SELECT
		  a.name AS author_name,
	    COUNT(c.hash) AS n_commits
	  FROM
	    authors a,
	    commits c
	  WHERE
	    a.name = c.author_name
	  GROUP BY
	    a.name
	) AS n_commits,
	(SELECT
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
    a.name) AS changes
WHERE
	n_commits.author_name = changes.author_name
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

CREATE VIEW files_ownership
AS
  SELECT
	ranked.file_path,
	n_changes.n_changes,
	n_authors.n_authors,
	ranked.primary_author,
	ranked.primary_author_n_commits,
	cast(ranked.primary_author_n_commits AS REAL) / cast(n_changes.n_changes AS REAL) AS primary_author_contribution,
	ranked.secondary_author,
	COALESCE(ranked.secondary_author_n_commits, 0) AS secondary_author_n_commits,
	COALESCE(cast(ranked.secondary_author_n_commits AS REAL) / cast(n_changes.n_changes AS REAL), 0) AS secondary_author_contribution,
	ranked.tertiary_author,
	COALESCE(ranked.tertiary_author_n_commits, 0) AS tertiary_author_n_commits,
	COALESCE(cast(ranked.tertiary_author_n_commits AS REAL) / cast(n_changes.n_changes AS REAL), 0) AS tertiary_author_contribution,
	COALESCE(n_changes.n_changes - (ranked.primary_author_n_commits + ranked.secondary_author_n_commits + ranked.tertiary_author_n_commits), 0) AS other_authors_n_commits,
	COALESCE(cast(n_changes.n_changes - (ranked.primary_author_n_commits + ranked.secondary_author_n_commits + ranked.tertiary_author_n_commits) AS REAL) /  cast(n_changes.n_changes AS REAL), 0) AS other_authors_contribution
FROM
	(SELECT
		ranked.file_path,
		MAX(ranked.author_name) FILTER (WHERE ranked."rank" = 1) AS primary_author,
		SUM(ranked.n_commits) FILTER (WHERE ranked."rank" = 1) AS primary_author_n_commits,
		MAX(ranked.author_name) FILTER (WHERE ranked."rank" = 2) AS secondary_author,
		SUM(ranked.n_commits) FILTER (WHERE ranked."rank" = 2) AS secondary_author_n_commits,
		MAX(ranked.author_name) FILTER (WHERE ranked."rank" = 3) AS tertiary_author,
		SUM(ranked.n_commits) FILTER (WHERE ranked."rank" = 3) AS tertiary_author_n_commits
	FROM
		(SELECT
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
		) AS ranked
	GROUP BY ranked.file_path) AS ranked,
	(SELECT s.file_path, COUNT(DISTINCT(c.author_name)) AS n_authors
  	FROM
	    commits c,
    	snapshots s
	WHERE
    	c.hash = s.commit_hash AND
    	s.changed = TRUE
  	GROUP BY
    	s.file_path
  	ORDER BY
    	n_authors DESC
	) AS n_authors,
	(SELECT
		f."path" AS file_path,
		COUNT(s.file_path) AS n_changes
	FROM
		files f JOIN snapshots s ON f.path = s.file_path
	WHERE
		s.changed = TRUE
	GROUP BY
		f."path") AS n_changes
WHERE
	n_authors.file_path = ranked.file_path AND
	ranked.file_path = n_changes.file_path
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

-- commits churn
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

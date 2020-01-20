-- authors
CREATE TABLE authors(
  name TEXT PRIMARY KEY,
  email_address TEXT NOT NULL
);


-- commits
CREATE TABLE commits(
  hash TEXT PRIMARY KEY,
  parent TEXT NULL,
  repo TEXT NOT NULL,
  date TEXT NOT NULL,
  tag TEXT NULL,
  message TEXT NOT NULL,
  author_name TEXT NOT NULL,
  FOREIGN KEY(author_name) REFERENCES authors(name)
);

CREATE INDEX commits_repo_ix ON commits(repo);


-- files
CREATE TABLE files(
  path TEXT PRIMARY KEY,
  language TEXT NULL,
  file_name TEXT NOT NULL,
  namespace TEXT NOT NULL,
  scope TEXT NOT NULL  -- something like main, test, it, e2e, etc. depending on source directory or file name
);

-- TODO: Maybe add repo to the file as well or some prefixed path for absolute location


-- snapshots
CREATE TABLE snapshots(
  commit_hash TEXT NOT NULL,
  file_path TEXT NOT NULL,
  lines_added INTEGER NOT NULL,
  lines_deleted INTEGER NOT NULL,
  code_lines INTEGER NOT NULL,
  comment_lines INTEGER NOT NULL,
  blank_lines INTEGER NOT NULL,
  total_lines INTEGER NOT NULL,
  complexity INTEGER NOT NULL,
  changed BOOLEAN NOT NULL, -- was the file actually changed in the commit
  FOREIGN KEY(commit_hash) REFERENCES commits(hash),
  FOREIGN KEY(file_path) REFERENCES files(path)
);

CREATE INDEX snapshots_commit_hash_ix ON snapshots(commit_hash);
CREATE INDEX snapshots_file_path_ix ON snapshots(file_path);

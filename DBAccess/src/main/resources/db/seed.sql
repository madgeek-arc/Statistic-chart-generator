-- One-time migration seed for existing data.
-- Run AFTER init.sql on the config-db.
-- All INSERT statements use ON CONFLICT DO NOTHING — safe to re-run.

-- -----------------------------------------------------------------------
-- Named queries (from namedqueries.properties)
-- -----------------------------------------------------------------------

INSERT INTO statstool_named_query (name, query, description)
VALUES (
    'test',
    'select count(DISTINCT r0.id), r0.year FROM result r0 JOIN project_results p1 ON r0.id=p1.result JOIN project p2 ON p1.id=p2.id WHERE r0.bestlicense=''Open Access'' AND (r0.year>=2000 AND r0.year<=2019) AND p2.funder=''European Commission'' AND (r0.type=''publication'' AND r0.type=''publication'' AND r0.type=''publication'' AND r0.type=''publication'' AND r0.type=''publication'' AND r0.type=''publication'') GROUP BY r0.year ORDER BY r0.year LIMIT 30',
    'Sample query: OA publications per year by EC-funded projects'
) ON CONFLICT (name) DO NOTHING;

INSERT INTO statstool_named_query (name, query, description)
VALUES (
    'thanos',
    'select o.country, count(distinct project_organizations.id) as no_projects, country2 as collaborating_country, collaborations as no_collaborations from indi_funder_country_collab ifc join organization o on o.country=ifc.country1 join project_organizations on project_organizations.organization=o.id where funder=''European Commission'' group by o.country, country2, collaborations',
    'Country collaboration statistics for EC-funded projects'
) ON CONFLICT (name) DO NOTHING;

-- -----------------------------------------------------------------------
-- Mapping profiles (from mappings.json + mapping.json / monitor.json / openaire_db.json)
--
-- For each profile, copy the JSON content of its .json file into the
-- mapping_content column.  The easiest way is to use psql's \set:
--
--   \set content `cat /path/to/mapping.json`
--   INSERT INTO statstool_mapping_profile (...) VALUES (..., :'content'::text);
--
-- Or use the POST /config/profiles API after the app is running.
-- -----------------------------------------------------------------------

-- Template — replace <MAPPING_JSON> with the full contents of mapping.json:
-- INSERT INTO statstool_mapping_profile
--   (name, description, usage_hint, shareholders, complexity, is_primary, is_hidden, mapping_content)
-- VALUES (
--   'OpenAIRE All-inclusive',
--   'Contains all the OpenAIRE information space and is compatible with the view in www.openaire.eu',
--   'Best used to create statistics for the entire information space',
--   '["All"]', 0, TRUE, FALSE,
--   '<MAPPING_JSON>'
-- ) ON CONFLICT (name) DO NOTHING;

-- Template for monitor profile (replace <MONITOR_JSON> with monitor.json contents):
-- INSERT INTO statstool_mapping_profile
--   (name, description, usage_hint, shareholders, complexity, is_primary, is_hidden, mapping_content)
-- VALUES (
--   'monitor',
--   'Contains all the OpenAIRE information space but its entities correspond to the original OpenAIRE schema',
--   'Best used to navigate the whole information space',
--   '["Admins","Moderators"]', 0, FALSE, FALSE,
--   '<MONITOR_JSON>'
-- ) ON CONFLICT (name) DO NOTHING;

-- Template for Stats db profile (replace <OPENAIRE_DB_JSON> with openaire_db.json contents):
-- INSERT INTO statstool_mapping_profile
--   (name, description, usage_hint, shareholders, complexity, is_primary, is_hidden, mapping_content)
-- VALUES (
--   'Stats db',
--   'Contains all the OpenAIRE information space but its entities correspond to the original OpenAIRE schema',
--   'Best used to navigate the whole information space',
--   '["Monitor Portal"]', 0, FALSE, TRUE,
--   '<OPENAIRE_DB_JSON>'
-- ) ON CONFLICT (name) DO NOTHING;

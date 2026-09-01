#!/bin/bash
# Decompose a macrobenchmark perfetto trace. $1 = trace file.
TP=/private/tmp/claude-501/-Users-vitor-Documents-workspace-Amethyst/c1db9f48-dfa5-437b-acc2-a33fcb5fe512/scratchpad/trace_processor
T="$1"
echo "############ RenderThread: self-time by slice name ############"
$TP -q "
with rt as (
  select s.id, s.name, s.dur, s.parent_id
  from slice s join thread_track tt on s.track_id=tt.id join thread t using(utid)
  where t.name like '%RenderThread%'
),
child as (select parent_id, sum(dur) d from rt where parent_id is not null group by parent_id)
select rt.name, count(*) n, round(sum(rt.dur - coalesce(child.d,0))/1e6,1) self_ms,
       round(sum(rt.dur)/1e6,1) total_ms
from rt left join child on child.parent_id = rt.id
group by rt.name order by self_ms desc limit 25;" "$T" 2>/dev/null

echo "############ Texture / atlas uploads (name + dimensions) ############"
$TP -q "
select s.name, count(*) n, round(sum(s.dur)/1e6,2) ms, round(avg(s.dur)/1e3,1) avg_us
from slice s join thread_track tt on s.track_id=tt.id join thread t using(utid)
where t.name like '%RenderThread%'
  and (s.name like '%upload%' or s.name like '%Texture%' or s.name like '%Atlas%'
       or s.name like '%glyph%' or s.name like '%Glyph%')
group by s.name order by ms desc limit 30;" "$T" 2>/dev/null

echo "############ Main thread: self-time by slice name ############"
$TP -q "
with mt as (
  select s.id, s.name, s.dur, s.parent_id
  from slice s join thread_track tt on s.track_id=tt.id join thread t using(utid)
  where t.is_main_thread = 1
),
child as (select parent_id, sum(dur) d from mt where parent_id is not null group by parent_id)
select mt.name, count(*) n, round(sum(mt.dur - coalesce(child.d,0))/1e6,1) self_ms
from mt left join child on child.parent_id = mt.id
group by mt.name order by self_ms desc limit 25;" "$T" 2>/dev/null

echo "############ Children of 'animation' (the 837ms mystery) ############"
$TP -q "
select c.name, count(*) n, round(sum(c.dur)/1e6,1) ms
from slice p join slice c on c.parent_id = p.id
where p.name = 'animation'
group by c.name order by ms desc limit 20;" "$T" 2>/dev/null

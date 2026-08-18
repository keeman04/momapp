#!/usr/bin/env bash
set -euo pipefail
python3 - <<'PY'
from pathlib import Path
p=Path('app/src/main/java/com/tanu/app/MainActivity.java')
s=p.read_text()
old='Button share=button("Share MOM",true);margin(share,14,0);content.addView(share);share.setOnClickListener(v->{haptic(v);shareText(lastMom);});}catch(Exception e){toast(e.getMessage());}}'
new='Button share=button("Share MOM",true);margin(share,14,0);content.addView(share);share.setOnClickListener(v->{haptic(v);shareText(lastMom);});Button delete=button("Delete this MOM",false);delete.setTextColor(RED);margin(delete,10,0);content.addView(delete);delete.setOnClickListener(v->{haptic(v);new AlertDialog.Builder(this).setTitle("Delete this MOM?").setMessage("This removes the saved MOM and transcript from TANU. This cannot be undone.").setNegativeButton("Cancel",null).setPositiveButton("Delete",(d,w)->{if(f.delete()){lastMom="";lastTranscript="";lastMeetingFile=null;toast("MOM deleted");history();}else toast("Could not delete MOM");}).show();});}catch(Exception e){toast(e.getMessage());}}'
if old not in s: raise SystemExit('saved meeting share block not found')
s=s.replace(old,new,1)
p.write_text(s)
PY
grep -q 'Delete this MOM' app/src/main/java/com/tanu/app/MainActivity.java

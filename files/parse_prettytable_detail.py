
import json
from docutils.parsers.rst import tableparser
from docutils import statemachine

table_file = open('${table_file}', 'r')
raw_data = table_file.read().split('\\n')
table_file.close()
pretty_raw_data = []
i = 1
for datum in raw_data:
  if datum != "":
    if datum[3] != ' ' and i > 4:
      pretty_raw_data.append(raw_data[0])
    if i == 3:
      pretty_raw_data.append(datum.replace('-', '='))
    else:
      pretty_raw_data.append(datum)
    i += 1
parser = tableparser.GridTableParser()
block = statemachine.StringList(pretty_raw_data)
docutils_data = parser.parse(block)
final_data = []
keys = []
for line in docutils_data[1]:
  for item in line:
     keys.append(' '.join(item[3]).strip())
for line in docutils_data[2]:
  final_line = {}
  i = 0
  for item in line:
    value = ' '.join(item[3]).strip()
    try:
      value = json.loads(value)
    except:
      pass
    final_line[keys[i]] = value
    i += 1
  final_data.append(final_line)
print json.dumps(final_data)
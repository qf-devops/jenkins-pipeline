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
final_data = {}
for line in docutils_data[2]:
  key = ' '.join(line[0][3]).strip()
  value = ' '.join(line[1][3]).strip()
  if key != "":
    try:
      value = json.loads(value)
    except:
      pass
    final_data[key] = value
  i+=1
print json.dumps(final_data)
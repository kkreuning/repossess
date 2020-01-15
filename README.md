# Daywalker

Code analysis time machine.

## Setup
setup requirements:
  - scala 2.13
  - sbt


for the notebook:

Python 3 with the following packages (installed through `pip`)

```
pip install notebook
pip install numpy
pip install pandas
pip install matplotlib
pip install jupyterlab
pip install ipython-sql
pip install plotly
```

For plotly you may need to perform additional installation steps: [https://plot.ly/python/getting-started/](https://plot.ly/python/getting-started/)

## Running

It's the easiest to run from sbt:

```
sbt "run -repo /path/to/git/repository -br branch_name -db outputdatabase.db --reset"
```

See code for command line arguments and options.

Top run the notebook, from the base directory run:

```
jupyter lab
```
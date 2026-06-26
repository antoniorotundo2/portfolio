---
id: "turbofan-rul"
title: "Turbofan Engine RUL Prediction"
description: "Predicting the Remaining Useful Life of jet engines from NASA C-MAPSS sensor time series — classical regressors vs LSTM/GRU networks"
tags:
  - Python
  - Machine Learning
  - Deep Learning
  - Time Series
  - Predictive Maintenance
  - TensorFlow
  - scikit-learn
status: "archived"
year: 2022
---

A **decision-support / predictive-maintenance** study: given streams of sensor readings from a fleet of aircraft turbofan engines, estimate each engine's **Remaining Useful Life (RUL)** — how many operational cycles are left before failure. Accurate RUL estimates let operators schedule maintenance *just in time*, avoiding both unexpected failures and wasteful early part replacement.

## The data: NASA C-MAPSS

The project uses NASA's **C-MAPSS** turbofan degradation dataset, made of four subsets of increasing difficulty:

| Subset | Operating conditions | Fault modes |
|---|---|---|
| FD001 | 1 (sea level) | 1 (HPC degradation) |
| FD002 | 6 | 1 |
| FD003 | 1 | 2 (HPC + fan) |
| FD004 | 6 | 2 |

Each engine is a **multivariate time series** of 3 operational settings and 21 noisy sensor measurements. Engines start healthy and degrade until failure; the goal is to predict the remaining cycles on the test set, where the series is truncated before failure.

## From raw cycles to training samples

For training, the **RUL label** of each cycle is simply how many cycles remain until that engine's last recorded cycle:

```python
def add_rul(df):
    grouped = df.groupby(by="engine_nr")
    max_cycle = grouped["time_cycles"].max()
    result = df.merge(max_cycle.to_frame(name="max_cycles"),
                      left_on="engine_nr", right_index=True)
    result["RUL"] = result["max_cycles"] - result["time_cycles"]
    return result
```

Each engine's signals are then cut into **sliding windows of 10 cycles**, so the models see a short slice of recent history rather than a single snapshot:

```python
for i in range(0, len(engine_data) - window_size):
    X_train.append(xs[i:i + window_size, :])     # 10 cycles × 21 sensors
    y_train.append(ys[i + window_size - 1])      # RUL at the window's end
```

Sensors are standardized with `StandardScaler`, and an optional **Butterworth low-pass filter** can be applied to denoise the raw signals before windowing.

## Models compared

The same pipeline feeds four regressors, from simple to sequential:

- **Linear Regression** and **Gradient Boosting** — on the flattened `10 × 21` window (a quick, strong classical baseline). SVR was also explored.
- **LSTM** and **GRU** — recurrent networks that consume the window as an actual sequence:

```python
model = keras.Sequential()
model.add(layers.LSTM(input_dim=len(sensor_names), units=5))
model.add(layers.ReLU())
model.add(layers.Dropout(0.3))
model.add(layers.Dense(1))
model.compile(optimizer="adam", loss="mse", metrics=[RootMeanSquaredError()])
```

Training uses Adam with MSE loss and **early stopping** (patience 4 on validation loss). Every model is evaluated on all four datasets with **MSE, RMSE and R²**, so it is easy to see how each approach copes as the number of operating conditions and fault modes grows.

## Takeaways

RUL prediction is a textbook **prognostics** problem and a great test bed for comparing paradigms: lightweight classical regressors versus recurrent sequence models, on time-series data that is noisy, multivariate and uneven in length. The harder subsets (FD002 and FD004, with six operating conditions and two fault modes) are markedly tougher than the single-condition FD001 — a reminder that, in real predictive maintenance, operating context matters as much as the model.

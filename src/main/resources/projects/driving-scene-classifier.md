---
id: "driving-scene-classifier"
title: "Autonomous Driving Scene Classifier"
description: "Six-class autonomous-driving image classifier built in C#/Emgu.CV with GLCM texture features and KNN — classic computer vision, no deep learning"
tags:
  - Computer Vision
  - C#
  - Emgu.CV
  - OpenCV
  - KNN
  - Feature Engineering
status: "archived"
year: 2022
---

The **final challenge** of a Computer Vision & Pattern Recognition course — *Object Classification for Autonomous Driving*, run as an official **CodaLab competition**. The task: classify outdoor driving images into **six object classes** — *pedestrian, cyclist, car, truck, tram, tricycle* — using *classic* computer vision, no neural networks. The classifier is written in **C# with Emgu.CV** (the .NET wrapper around OpenCV) and describes each image with just **four texture numbers** before handing them to a **K-Nearest-Neighbours** model.

## The challenge

The competition provides **28,554 fully-labeled RGB images** — a **22,249**-image training set and a **6,305**-image test set — across the six classes. The data is **strongly imbalanced** (far more cars than pedestrians) and highly varied, with day- and night-time acquisitions, different cities and changing weather. The organisers kept **two separate leaderboards** — one for *hand-crafted feature* approaches and one for *neural networks* — both ranked by **Mean Class Accuracy (MCA)**. This solution deliberately took the **hand-crafted feature** route.

## Texture as a feature

Driving scenes differ strongly in texture — a smooth highway versus a busy crossing versus a roundabout. To capture this, each image is summarised with a **Gray-Level Co-occurrence Matrix (GLCM)**: a `256 × 256` table counting how often two intensity levels occur next to each other for a given direction. To remove orientation bias, four oriented matrices are averaged into a single **unoriented** one.

```csharp
private Matrix<double> ComputeUnorientedCoOccurrenceMatrix(Image<Bgr, byte> img)
{
    var m1 = ComputeCoOccurrenceMatrix(img, -1,  0);
    var m2 = ComputeCoOccurrenceMatrix(img, -1, -1);
    var m3 = ComputeCoOccurrenceMatrix(img,  0, -1);
    var m4 = ComputeCoOccurrenceMatrix(img, +1, -1);
    return m1.Add(m2).Add(m3).Add(m4) / 4;
}
```

## From a 256×256 matrix to four numbers

The full matrix is far too large to classify directly, so it is condensed into four classic **Haralick descriptors**:

- **Energy** — `√Σ p(i,j)²` — high for uniform texture.
- **Entropy** — `−Σ p(i,j)·log p(i,j)` — high for random/complex texture.
- **Contrast** — `Σ (i−j)²·p(i,j)` — large local intensity differences.
- **Homogeneity** — `Σ p(i,j) / (1+|i−j|)` — high when co-occurring values are close.

Each image therefore becomes a compact 4-D feature vector, which after min–max normalization feeds Emgu.CV's KNN classifier:

```csharp
knn = new KNearest();
knn.DefaultK = K;            // K = 1 by default
knn.Train(new TrainData(tData, DataLayoutType.RowSample, tLab));
```

## Handling a heavily imbalanced dataset

The training set is strongly skewed — the **car** class alone held over **12,000** images while the rarest had under a thousand. Inverse-frequency **class weights** drive an on-the-fly **downsampling** of the dominant classes, keeping the per-class counts balanced without discarding the signal entirely.

## Evaluation

With six classes, global accuracy hides per-class failures, so the pipeline reports **precision, recall and F1 per class**, a full **6×6 confusion matrix**, and the **Mean Class Accuracy (MCA)** — the competition's official ranking metric, and far more honest on imbalanced data. The same pipeline finally runs over the challenge's evaluation set and writes a `submission.txt` for CodaLab submission.

## Takeaways

This will not beat a fine-tuned CNN on raw accuracy, but it is **tiny, fast, GPU-free and fully interpretable**: every feature has a clear physical meaning, so you can reason about *why* a scene was classified a certain way. Implementing GLCMs and Haralick features by hand makes the link between texture and classification tangible in a way that calling `model.fit()` never does.

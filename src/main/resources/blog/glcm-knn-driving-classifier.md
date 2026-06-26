---
title: "Classifying Driving Scenes with Texture: GLCM + KNN in C#"
excerpt: "How four hand-crafted Haralick texture features and a humble KNN can classify autonomous-driving images — no deep learning required."
tags:
  - Computer Vision
  - C#
  - Emgu.CV
  - KNN
  - Feature Engineering
publishedAt: "2026-06-26"
readingMinutes: 8
---

Not every image-classification problem needs a deep neural network. For a computer-vision *final challenge* — classifying autonomous-driving images into **six categories** — I built a classifier in **C# with Emgu.CV** (the .NET wrapper around OpenCV) using nothing but **texture statistics** and a **K-Nearest-Neighbours** model. The whole feature vector is just **four numbers per image**. Here is how it works, and why such a compact approach is more interesting than it sounds.

## The idea: describe an image by its texture

Driving scenes differ a lot in *texture*: a highway is smooth and uniform, an urban crossing is busy and high-contrast, a roundabout has its own repeating structure. A classic way to capture texture is the **Gray-Level Co-occurrence Matrix (GLCM)**.

A GLCM counts how often a pixel with intensity `i` sits next to a pixel with intensity `j`, for a given offset direction. For 8-bit images that gives a `256 × 256` matrix of (normalized) co-occurrence frequencies.

A single direction is sensitive to orientation, so I average four oriented matrices into an **unoriented** one:

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

A raw co-occurrence matrix is far too large to feed a classifier directly. Instead I summarise it with four classic **Haralick descriptors**:

- **Energy** — `√Σ p(i,j)²` — high when texture is uniform.
- **Entropy** — `−Σ p(i,j)·log p(i,j)` — high when texture is random/complex.
- **Contrast** — `Σ (i−j)²·p(i,j)` — large local intensity differences.
- **Homogeneity** — `Σ p(i,j) / (1+|i−j|)` — high when co-occurring values are close.

That collapses each image into a tidy 4-D feature vector:

```csharp
float[] ExtractFeatureVector(Matrix<double> m)
{
    return new float[] {
        (float)ComputeEnergy(m),
        (float)ComputeEntropy(m),
        (float)ComputeContrast(m),
        (float)ComputeHomogeneity(m),
    };
}
```

After min–max normalization (computed on the training set and reused at inference), the vectors go straight into Emgu.CV's **KNN** classifier:

```csharp
knn = new KNearest();
knn.DefaultK = K;            // K = 1 by default
knn.Train(new TrainData(tData, DataLayoutType.RowSample, tLab));
```

Classifying a new frame is then just: compute its unoriented GLCM, extract the four features, normalize, and ask the KNN for the nearest training sample's label.

## Dealing with a very imbalanced dataset

The training set (~22k images) is heavily skewed: one class had **12,181** images while the rarest had under a thousand. Left unchecked, KNN would simply favour the majority class. I computed inverse-frequency **class weights** and used them to **downsample** over-represented classes on the fly:

```csharp
weights[c] = N / (6 * occurrencies[c]);   // inverse frequency
// ...later, while loading:
if (downsampling && rnd.NextDouble() > weights[c]) continue;
```

This keeps the per-class counts closer to balanced without throwing away the signal entirely.

## Measuring what matters

With six classes, plain accuracy hides per-class failures, so the evaluation reports **precision, recall and F1 per class**, a full **6×6 confusion matrix**, and the **Mean Class Accuracy (MCA)** — the average of the per-class accuracies, which is far more honest on imbalanced data than global accuracy. The same pipeline finally runs over the challenge's evaluation set and writes a `submission.txt` of predictions.

## Why bother, in the deep-learning era?

This approach will not beat a fine-tuned CNN on raw accuracy. But it has qualities that are easy to forget:

- **Tiny and fast** — four features per image, a non-parametric classifier, no GPU.
- **Interpretable** — every feature has a clear physical meaning; you can reason about *why* a scene was classified a certain way.
- **A great teacher** — implementing GLCMs and Haralick features by hand makes the connection between texture and classification tangible in a way that calling `model.fit()` never does.

Sometimes the most instructive solution is the one you can hold entirely in your head — four numbers, a distance metric, and a clear-eyed look at the confusion matrix.

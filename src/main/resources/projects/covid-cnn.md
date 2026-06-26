---
id: "covid-cnn"
title: "COVID-19 Detection with CNNs"
description: "Convolutional neural networks classifying chest X-rays as healthy or COVID-19 — a custom CNN benchmarked against AlexNet and transfer-learning models"
tags:
  - Deep Learning
  - Computer Vision
  - CNN
  - TensorFlow
  - Keras
  - Python
  - Medical Imaging
  - Transfer Learning
status: "archived"
year: 2022
---

A **computer-vision** study on using **convolutional neural networks** to classify medical images: given a chest radiograph, the model decides whether the patient is **healthy** or affected by **COVID-19**. The goal was to compare custom and transfer-learning architectures and to show how to train them effectively even with **limited (CPU-only) compute**.

## Dataset

The data comes from a public **Kaggle** chest X-ray dataset ([competition csc532](https://www.kaggle.com/c/csc532)), split into training, validation and test sets, each with two classes — *healthy* and *covid*. Every split contains **290 healthy** and **118 covid** images, so the problem is moderately **imbalanced** toward the healthy class.

![Sample chest X-rays: a COVID-19 case and a healthy case](/static/img/cnn_dataset.png)

## Approach

Four architectures were trained with **TensorFlow 2.8 / Keras**:

- **BasicCNN** — a custom network: three convolutional blocks (ReLU + max-pooling), `Flatten`, dropout at 50%, and two dense layers (512 and 256 units) before the softmax classifier.
- **AlexNet** — the classic deeper architecture BasicCNN is inspired by.
- **ResNet-50** and **MobileNetV2** — pretrained on ImageNet and adapted via **transfer learning**.

To fight overfitting on a relatively small dataset, each image is randomly altered every epoch through **data augmentation** — random rotation, contrast and zoom — so the model effectively never sees the exact same picture twice.

![Three randomly augmented versions of a chest X-ray](/static/img/cnn_augmentation.png)

Training ran for up to **100 epochs** with **early stopping** (patience 4 on validation loss), a learning rate of `0.01` and a batch size of `32`, entirely on **CPU**. Models were evaluated with accuracy, precision, recall, F1 and ROC-AUC, plus confusion matrices.

## Results

| Network | Test loss | Accuracy | Precision | Recall | F1 | AUC |
|---|---|---|---|---|---|---|
| **BasicCNN** | **0.2362** | **90.20%** | **90.20%** | **90.20%** | **90.20%** | **96.82%** |
| AlexNet | 0.2486 | 88.97% | 88.97% | 88.97% | 88.97% | 96.29% |
| ResNet-50 | 0.6032 | 71.08% | 71.08% | 71.08% | 71.08% | 71.08% |
| MobileNetV2 | 0.6154 | 71.08% | 71.08% | 71.08% | 71.08% | 71.08% |

The custom **BasicCNN** was the clear winner, reaching **90.2% accuracy** and **96.8% AUC**. The confusion matrix below shows it correctly identifies the vast majority of both classes.

![BasicCNN confusion matrix](/static/img/cnn_confusion_basiccnn.png)

Interestingly, the **transfer-learning** models (ResNet-50, MobileNetV2) underperformed, collapsing to always predict the majority *healthy* class (71.08% = the share of healthy images). Two likely reasons: their ImageNet pretraining produces features poorly suited to radiographs, and the limited CPU budget prevented longer fine-tuning.

## Conclusions

The experiment confirms that well-shaped CNNs can reach strong accuracy on medical-image classification even without GPUs, and that — for this domain and compute budget — a compact custom network beat much larger pretrained ones. Outlined future improvements include **Leaky ReLU** activations, replacing some pooling layers with strided convolutions, and training on GPU hardware to close the gap with the top Kaggle leaderboard scores (>92%).

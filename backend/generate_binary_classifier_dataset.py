import pandas as pd
import random
import re

print("Loading original dataset...")
df = pd.read_csv("sorted_cleaned_dataset.csv")
df["Name of medicine"] = df["Name of medicine"].astype(str).str.lower().str.strip()

medicine_names = df["Name of medicine"].unique().tolist()
print("Total unique medicine names:", len(medicine_names))

# -----------------------------
#  Generate Negative Samples
# -----------------------------

print("Generating negative samples...")

noise_words = set()

# 1) Add text from descriptions, side effects (non-medicine words)
for col in ["Full Description", "Side Effects"]:
    if col in df.columns:
        texts = " ".join(df[col].dropna().astype(str).tolist())
        tokens = re.findall(r"[A-Za-z]+", texts.lower())
        for t in tokens:
            if len(t) > 3 and t not in medicine_names:
                noise_words.add(t)
# 2) Add common OCR garbage
ocr_noise = [
    "tablet", "tablets", "capsule", "capsules", "composition",
    "schedule", "expiry", "exp", "mfg", "store", "protected",
    "moisture", "keep", "dosage", "license", "price", "strip",
    "mg", "ml", "wfi", "ip", "bp", "usp"
]
noise_words.update(ocr_noise)

# 3) Add random fake uppercase words
for _ in range(20000):
    fake = ''.join(random.choices("ABCDEFGHIJKLMNOPQRSTUVWXYZ", k=random.randint(4,8)))
    noise_words.add(fake.lower())

noise_words = list(noise_words)
print("Total generated noise samples:", len(noise_words))

# ---------------------------------------
# Construct Final Binary Dataset
# ---------------------------------------
print("Building final binary dataset...")

# Label 1 → real medicine
pos_samples = [{"text": m, "label": 1} for m in medicine_names]

# Label 0 → noise
neg_samples = [{"text": w, "label": 0} for w in noise_words]

dataset = pos_samples + neg_samples
random.shuffle(dataset)

df_final = pd.DataFrame(dataset)
df_final.to_csv("binary_classifier_training.csv", index=False)

print(" Binary dataset ready!")
print(" File saved: binary_classifier_training.csv")
print(" Total samples:", len(df_final))
print(df_final.head())

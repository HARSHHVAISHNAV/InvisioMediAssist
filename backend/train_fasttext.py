import fasttext

TRAIN_FILE = "fasttext_medicine_train.txt"
MODEL_FILE = "medicine_fasttext.bin"

print("🚀 Training FastText model...")

model = fasttext.train_supervised(
    input=TRAIN_FILE,
    lr=0.5,
    epoch=15,
    wordNgrams=3,
    bucket=500000,
    dim=100,
    loss="softmax"
)

model.save_model(MODEL_FILE)
print("✅ Model saved as:", MODEL_FILE)

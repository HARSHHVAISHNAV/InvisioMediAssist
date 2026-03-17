import base64
import io
import json
import pandas as pd
import spacy
from flask import Flask, request, jsonify
from PIL import Image
import cv2
import numpy as np
import google.cloud.vision as vision
from fuzzywuzzy import fuzz, process
import os
from flask_cors import CORS
import datetime

# Translation
from google.cloud import translate_v2 as translate

app = Flask(__name__)
CORS(app)

os.environ["GOOGLE_APPLICATION_CREDENTIALS"] = "D:/InvisioAssist-Backend/invisiomediassist-key.json"

# ---------------------- DATA & MODEL SETUP ----------------------
CSV_PATH = "sorted_cleaned_dataset.csv"
df = pd.read_csv(CSV_PATH)
df["Name of medicine"] = df["Name of medicine"].str.lower().str.strip()

med7 = spacy.load("en_core_med7_lg")
client = vision.ImageAnnotatorClient()

translate_client = translate.Client()

HISTORY_FILE = "scan_history.json"

# ---------------------- HISTORY HELPERS ----------------------

def save_to_history(medicine_name):
    history = []

    if os.path.exists(HISTORY_FILE):
        with open(HISTORY_FILE, "r") as f:
            history = json.load(f)

    history.append({
        "medicine": medicine_name,
        "time": datetime.datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    })

    history = history[-10:]

    with open(HISTORY_FILE, "w") as f:
        json.dump(history, f, indent=2)


@app.route("/get_history", methods=["GET"])
def get_history():

    if os.path.exists(HISTORY_FILE):
        with open(HISTORY_FILE, "r") as f:
            history = json.load(f)
        return jsonify({"history": history}), 200
    else:
        return jsonify({"history": []}), 200


# ---------------------- LANGUAGE HELPERS ----------------------

def detect_language(text):
    result = translate_client.detect_language(text)
    return result["language"]


def translate_text(text, target_lang):
    result = translate_client.translate(text, target_language=target_lang)
    return result["translatedText"]


# ---------------------- OCR HELPERS ----------------------

def extract_text_google_vision(image_bytes):

    image = vision.Image(content=image_bytes)
    response = client.text_detection(image=image)
    texts = response.text_annotations

    return texts[0].description if texts else ""


def extract_medicine_name_with_med7(text):

    doc = med7(text)
    med_names = [ent.text.lower() for ent in doc.ents if ent.label_ == "DRUG"]

    return med_names[0] if med_names else None


def find_best_match(input_text):

    choices = df["Name of medicine"].dropna().unique()

    best_match, score = process.extractOne(
        input_text,
        choices,
        scorer=fuzz.token_sort_ratio
    )

    return best_match if score > 60 else None


# ---------------------- MAIN ROUTE ----------------------

@app.route("/process_image", methods=["POST"])
def process_image():

    try:

        data = request.get_json()
        image_b64 = data.get("image")
        user_lang = data.get("language", "en")

        if not image_b64:
            return jsonify({"error": "Image not found"}), 400

        image_bytes = base64.b64decode(image_b64)
        image = Image.open(io.BytesIO(image_bytes))

        img_byte_arr = io.BytesIO()
        image.save(img_byte_arr, format="PNG")
        image_bytes = img_byte_arr.getvalue()

        # -------- OCR --------
        extracted_text = extract_text_google_vision(image_bytes)

        print("OCR TEXT:", extracted_text)

        # -------- Language Detection --------
        detected_lang = detect_language(extracted_text)

        print("Detected Language:", detected_lang)

        if detected_lang != "en":
            extracted_text = translate_text(extracted_text, "en")

        print("Translated OCR:", extracted_text)

        # -------- MED7 --------
        detected_medicine = extract_medicine_name_with_med7(extracted_text)

        match_type = ""

        if detected_medicine:

            filtered_df = df[df["Name of medicine"] == detected_medicine]
            match_type = "Med7 direct match"

            if filtered_df.empty:

                candidates = df[
                    df["Name of medicine"].str.contains(
                        detected_medicine,
                        case=False,
                        na=False
                    )
                ]

                if not candidates.empty:

                    filtered_df = candidates
                    detected_medicine = candidates.iloc[0]["Name of medicine"]
                    match_type = "Partial match"

                else:

                    detected_medicine = find_best_match(detected_medicine)
                    filtered_df = df[df["Name of medicine"] == detected_medicine]
                    match_type = "Fuzzy match after Med7"

        else:

            detected_medicine = find_best_match(extracted_text)
            filtered_df = df[df["Name of medicine"] == detected_medicine]
            match_type = "Fuzzy match (Med7 fail)"

        if not detected_medicine or filtered_df.empty:
            return jsonify({"error": "Medicine not recognized"}), 200

        medicine_info = filtered_df.iloc[0]

        save_to_history(detected_medicine)

        response = {
            "medicine_name": detected_medicine,
            "description": medicine_info.get("Full Description", ""),
            "side_effects": medicine_info.get("Side Effects", ""),
            "match_type": match_type
        }

        # -------- TRANSLATE OUTPUT --------
        if user_lang != "en":

            response["medicine_name"] = translate_text(
                response["medicine_name"], user_lang
            )

            response["description"] = translate_text(
                response["description"], user_lang
            )

            response["side_effects"] = translate_text(
                response["side_effects"], user_lang
            )

        return jsonify(response), 200

    except Exception as e:
        return jsonify({"error": str(e)}), 500


# ---------------------- RUN SERVER ----------------------

if __name__ == "__main__":
    app.run(debug=True)
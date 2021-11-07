import os
import sys
from datetime import datetime
from typing import Dict

class PredictorConfig:
    @staticmethod
    def int_or_default(variable, default):
        if variable is None:
            return default
        else:
            return int(variable)

    def __str__(self) -> str:
        results = [
            "{}={}".format("MODEL_DIR", self.MODEL_DIR),
            "{}={}".format("FC_SIZE", self.FC_SIZE),
        ]
        return "\n".join(results)

    def __init__(self):
        self.MODEL_DIR = os.getenv('MODEL_DIR') or "/models"
        self.FC_SIZE = self.int_or_default(os.getenv('FC_SIZE'), 128)

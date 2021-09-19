FROM pytorch/pytorch:1.9.0-cuda10.2-cudnn7-runtime

RUN pip3 install minio

RUN mkdir /opt/intent-classification
COPY train.py /opt/intent-classification/
WORKDIR /opt/intent-classification

# Add folder for the logs.
RUN mkdir /model
RUN mkdir /logs

ENV EPOCHS=20
ENV LR=5
ENV BATCH_SIZE=64
ENV FC_SIZE=128
ENV MINIO_SERVER="127.0.0.2:9000"
ENV MINIO_SERVER_ACCESS_KEY="foooo"
ENV MINIO_SERVER_SECRET_KEY="barbarbar"
ENV TRAINING_DATA_BUCKET="mini-automl-dm"
ENV TRAINING_DATA_PATH="versionedDatasets/1/hashBA=="
ENV MODEL_BUCKET="mini-automl-serving"
ENV MODEL_ID="aaf98dsfase"
ENV MODEL_VERSION="1"
ENV WORLD_SIZE="1"
ENV RANK="0"
ENV MASTER_ADDR="localhost"
ENV MASTER_PORT="12356"

RUN chgrp -R 0 /opt/intent-classification \
  && chmod -R g+rwX /opt/intent-classification \
  && chgrp -R 0 /model \
  && chmod -R g+rwX /model \
  && chgrp -R 0 /logs \
  && chmod -R g+rwX /logs

ENTRYPOINT ["python3", "/opt/intent-classification/train.py"]

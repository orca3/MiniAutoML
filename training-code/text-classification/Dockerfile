# docker build -t orca3/intent-classification-predictor:latest -f predictor/Dockerfile predictor
FROM pytorch/pytorch:1.9.0-cuda10.2-cudnn7-runtime

RUN pip3 install minio protobuf grpcio torch-model-archiver

RUN mkdir /opt/intent-classification
COPY *.py /opt/intent-classification/
WORKDIR /opt/intent-classification

# Add folder for the logs.
RUN mkdir /model
RUN mkdir /logs

RUN chgrp -R 0 /opt/intent-classification \
  && chmod -R g+rwX /opt/intent-classification \
  && chgrp -R 0 /model \
  && chmod -R g+rwX /model \
  && chgrp -R 0 /logs \
  && chmod -R g+rwX /logs

ENTRYPOINT ["python3", "-u", "/opt/intent-classification/train.py"]

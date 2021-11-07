FROM pytorch/pytorch:1.9.0-cuda10.2-cudnn7-runtime

RUN pip3 install Flask==2.0.1

RUN mkdir /opt/intent-predictor
COPY *.py /opt/intent-predictor
WORKDIR /opt/intent-predictor

# Add folder for the logs.
RUN mkdir /models
RUN mkdir /logs

ENV MODEL_DIR=/models
EXPOSE 5000

RUN chgrp -R 0 /opt/intent-predictor \
  && chmod -R g+rwX /opt/intent-predictor \
  && chgrp -R 0 /models \
  && chmod -R g+rwX /models \
  && chgrp -R 0 /logs \
  && chmod -R g+rwX /logs

ENTRYPOINT ["python3", "-u", "/opt/intent-predictor/predict.py"]

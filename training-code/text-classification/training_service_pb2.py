# -*- coding: utf-8 -*-
# Generated by the protocol buffer compiler.  DO NOT EDIT!
# source: training_service.proto
"""Generated protocol buffer code."""
from google.protobuf.internal import enum_type_wrapper
from google.protobuf import descriptor as _descriptor
from google.protobuf import message as _message
from google.protobuf import reflection as _reflection
from google.protobuf import symbol_database as _symbol_database
# @@protoc_insertion_point(imports)

_sym_db = _symbol_database.Default()




DESCRIPTOR = _descriptor.FileDescriptor(
  name='training_service.proto',
  package='training',
  syntax='proto3',
  serialized_options=b'\n\035org.orca3.miniAutoML.trainingB\024TrainingServiceProtoP\001',
  create_key=_descriptor._internal_create_key,
  serialized_pb=b'\n\x16training_service.proto\x12\x08training\"?\n\x0cTrainRequest\x12/\n\x08metadata\x18\x01 \x01(\x0b\x32\x1d.training.TrainingJobMetadata\"\x1f\n\rTrainResponse\x12\x0e\n\x06job_id\x18\x01 \x01(\x05\"*\n\x18GetTrainingStatusRequest\x12\x0e\n\x06job_id\x18\x01 \x01(\x05\"\xb0\x01\n\x19GetTrainingStatusResponse\x12(\n\x06status\x18\x01 \x01(\x0e\x32\x18.training.TrainingStatus\x12\x0e\n\x06job_id\x18\x02 \x01(\x05\x12\x0f\n\x07message\x18\x03 \x01(\t\x12/\n\x08metadata\x18\x04 \x01(\x0b\x32\x1d.training.TrainingJobMetadata\x12\x17\n\x0fpositionInQueue\x18\x05 \x01(\x05\"\xe1\x01\n\x13TrainingJobMetadata\x12\x11\n\talgorithm\x18\x01 \x01(\t\x12\x12\n\ndataset_id\x18\x02 \x01(\t\x12\x0c\n\x04name\x18\x03 \x01(\t\x12\x1f\n\x17train_data_version_hash\x18\x04 \x01(\t\x12\x41\n\nparameters\x18\x05 \x03(\x0b\x32-.training.TrainingJobMetadata.ParametersEntry\x1a\x31\n\x0fParametersEntry\x12\x0b\n\x03key\x18\x01 \x01(\t\x12\r\n\x05value\x18\x02 \x01(\t:\x02\x38\x01*P\n\x0eTrainingStatus\x12\x0b\n\x07queuing\x10\x00\x12\n\n\x06launch\x10\x01\x12\x0b\n\x07running\x10\x02\x12\x0b\n\x07succeed\x10\x03\x12\x0b\n\x07\x66\x61ilure\x10\x04\x32\xa9\x01\n\x0fTrainingService\x12\x38\n\x05Train\x12\x16.training.TrainRequest\x1a\x17.training.TrainResponse\x12\\\n\x11GetTrainingStatus\x12\".training.GetTrainingStatusRequest\x1a#.training.GetTrainingStatusResponseB7\n\x1dorg.orca3.miniAutoML.trainingB\x14TrainingServiceProtoP\x01\x62\x06proto3'
)

_TRAININGSTATUS = _descriptor.EnumDescriptor(
  name='TrainingStatus',
  full_name='training.TrainingStatus',
  filename=None,
  file=DESCRIPTOR,
  create_key=_descriptor._internal_create_key,
  values=[
    _descriptor.EnumValueDescriptor(
      name='queuing', index=0, number=0,
      serialized_options=None,
      type=None,
      create_key=_descriptor._internal_create_key),
    _descriptor.EnumValueDescriptor(
      name='launch', index=1, number=1,
      serialized_options=None,
      type=None,
      create_key=_descriptor._internal_create_key),
    _descriptor.EnumValueDescriptor(
      name='running', index=2, number=2,
      serialized_options=None,
      type=None,
      create_key=_descriptor._internal_create_key),
    _descriptor.EnumValueDescriptor(
      name='succeed', index=3, number=3,
      serialized_options=None,
      type=None,
      create_key=_descriptor._internal_create_key),
    _descriptor.EnumValueDescriptor(
      name='failure', index=4, number=4,
      serialized_options=None,
      type=None,
      create_key=_descriptor._internal_create_key),
  ],
  containing_type=None,
  serialized_options=None,
  serialized_start=585,
  serialized_end=665,
)
_sym_db.RegisterEnumDescriptor(_TRAININGSTATUS)

TrainingStatus = enum_type_wrapper.EnumTypeWrapper(_TRAININGSTATUS)
queuing = 0
launch = 1
running = 2
succeed = 3
failure = 4



_TRAINREQUEST = _descriptor.Descriptor(
  name='TrainRequest',
  full_name='training.TrainRequest',
  filename=None,
  file=DESCRIPTOR,
  containing_type=None,
  create_key=_descriptor._internal_create_key,
  fields=[
    _descriptor.FieldDescriptor(
      name='metadata', full_name='training.TrainRequest.metadata', index=0,
      number=1, type=11, cpp_type=10, label=1,
      has_default_value=False, default_value=None,
      message_type=None, enum_type=None, containing_type=None,
      is_extension=False, extension_scope=None,
      serialized_options=None, file=DESCRIPTOR,  create_key=_descriptor._internal_create_key),
  ],
  extensions=[
  ],
  nested_types=[],
  enum_types=[
  ],
  serialized_options=None,
  is_extendable=False,
  syntax='proto3',
  extension_ranges=[],
  oneofs=[
  ],
  serialized_start=36,
  serialized_end=99,
)


_TRAINRESPONSE = _descriptor.Descriptor(
  name='TrainResponse',
  full_name='training.TrainResponse',
  filename=None,
  file=DESCRIPTOR,
  containing_type=None,
  create_key=_descriptor._internal_create_key,
  fields=[
    _descriptor.FieldDescriptor(
      name='job_id', full_name='training.TrainResponse.job_id', index=0,
      number=1, type=5, cpp_type=1, label=1,
      has_default_value=False, default_value=0,
      message_type=None, enum_type=None, containing_type=None,
      is_extension=False, extension_scope=None,
      serialized_options=None, file=DESCRIPTOR,  create_key=_descriptor._internal_create_key),
  ],
  extensions=[
  ],
  nested_types=[],
  enum_types=[
  ],
  serialized_options=None,
  is_extendable=False,
  syntax='proto3',
  extension_ranges=[],
  oneofs=[
  ],
  serialized_start=101,
  serialized_end=132,
)


_GETTRAININGSTATUSREQUEST = _descriptor.Descriptor(
  name='GetTrainingStatusRequest',
  full_name='training.GetTrainingStatusRequest',
  filename=None,
  file=DESCRIPTOR,
  containing_type=None,
  create_key=_descriptor._internal_create_key,
  fields=[
    _descriptor.FieldDescriptor(
      name='job_id', full_name='training.GetTrainingStatusRequest.job_id', index=0,
      number=1, type=5, cpp_type=1, label=1,
      has_default_value=False, default_value=0,
      message_type=None, enum_type=None, containing_type=None,
      is_extension=False, extension_scope=None,
      serialized_options=None, file=DESCRIPTOR,  create_key=_descriptor._internal_create_key),
  ],
  extensions=[
  ],
  nested_types=[],
  enum_types=[
  ],
  serialized_options=None,
  is_extendable=False,
  syntax='proto3',
  extension_ranges=[],
  oneofs=[
  ],
  serialized_start=134,
  serialized_end=176,
)


_GETTRAININGSTATUSRESPONSE = _descriptor.Descriptor(
  name='GetTrainingStatusResponse',
  full_name='training.GetTrainingStatusResponse',
  filename=None,
  file=DESCRIPTOR,
  containing_type=None,
  create_key=_descriptor._internal_create_key,
  fields=[
    _descriptor.FieldDescriptor(
      name='status', full_name='training.GetTrainingStatusResponse.status', index=0,
      number=1, type=14, cpp_type=8, label=1,
      has_default_value=False, default_value=0,
      message_type=None, enum_type=None, containing_type=None,
      is_extension=False, extension_scope=None,
      serialized_options=None, file=DESCRIPTOR,  create_key=_descriptor._internal_create_key),
    _descriptor.FieldDescriptor(
      name='job_id', full_name='training.GetTrainingStatusResponse.job_id', index=1,
      number=2, type=5, cpp_type=1, label=1,
      has_default_value=False, default_value=0,
      message_type=None, enum_type=None, containing_type=None,
      is_extension=False, extension_scope=None,
      serialized_options=None, file=DESCRIPTOR,  create_key=_descriptor._internal_create_key),
    _descriptor.FieldDescriptor(
      name='message', full_name='training.GetTrainingStatusResponse.message', index=2,
      number=3, type=9, cpp_type=9, label=1,
      has_default_value=False, default_value=b"".decode('utf-8'),
      message_type=None, enum_type=None, containing_type=None,
      is_extension=False, extension_scope=None,
      serialized_options=None, file=DESCRIPTOR,  create_key=_descriptor._internal_create_key),
    _descriptor.FieldDescriptor(
      name='metadata', full_name='training.GetTrainingStatusResponse.metadata', index=3,
      number=4, type=11, cpp_type=10, label=1,
      has_default_value=False, default_value=None,
      message_type=None, enum_type=None, containing_type=None,
      is_extension=False, extension_scope=None,
      serialized_options=None, file=DESCRIPTOR,  create_key=_descriptor._internal_create_key),
    _descriptor.FieldDescriptor(
      name='positionInQueue', full_name='training.GetTrainingStatusResponse.positionInQueue', index=4,
      number=5, type=5, cpp_type=1, label=1,
      has_default_value=False, default_value=0,
      message_type=None, enum_type=None, containing_type=None,
      is_extension=False, extension_scope=None,
      serialized_options=None, file=DESCRIPTOR,  create_key=_descriptor._internal_create_key),
  ],
  extensions=[
  ],
  nested_types=[],
  enum_types=[
  ],
  serialized_options=None,
  is_extendable=False,
  syntax='proto3',
  extension_ranges=[],
  oneofs=[
  ],
  serialized_start=179,
  serialized_end=355,
)


_TRAININGJOBMETADATA_PARAMETERSENTRY = _descriptor.Descriptor(
  name='ParametersEntry',
  full_name='training.TrainingJobMetadata.ParametersEntry',
  filename=None,
  file=DESCRIPTOR,
  containing_type=None,
  create_key=_descriptor._internal_create_key,
  fields=[
    _descriptor.FieldDescriptor(
      name='key', full_name='training.TrainingJobMetadata.ParametersEntry.key', index=0,
      number=1, type=9, cpp_type=9, label=1,
      has_default_value=False, default_value=b"".decode('utf-8'),
      message_type=None, enum_type=None, containing_type=None,
      is_extension=False, extension_scope=None,
      serialized_options=None, file=DESCRIPTOR,  create_key=_descriptor._internal_create_key),
    _descriptor.FieldDescriptor(
      name='value', full_name='training.TrainingJobMetadata.ParametersEntry.value', index=1,
      number=2, type=9, cpp_type=9, label=1,
      has_default_value=False, default_value=b"".decode('utf-8'),
      message_type=None, enum_type=None, containing_type=None,
      is_extension=False, extension_scope=None,
      serialized_options=None, file=DESCRIPTOR,  create_key=_descriptor._internal_create_key),
  ],
  extensions=[
  ],
  nested_types=[],
  enum_types=[
  ],
  serialized_options=b'8\001',
  is_extendable=False,
  syntax='proto3',
  extension_ranges=[],
  oneofs=[
  ],
  serialized_start=534,
  serialized_end=583,
)

_TRAININGJOBMETADATA = _descriptor.Descriptor(
  name='TrainingJobMetadata',
  full_name='training.TrainingJobMetadata',
  filename=None,
  file=DESCRIPTOR,
  containing_type=None,
  create_key=_descriptor._internal_create_key,
  fields=[
    _descriptor.FieldDescriptor(
      name='algorithm', full_name='training.TrainingJobMetadata.algorithm', index=0,
      number=1, type=9, cpp_type=9, label=1,
      has_default_value=False, default_value=b"".decode('utf-8'),
      message_type=None, enum_type=None, containing_type=None,
      is_extension=False, extension_scope=None,
      serialized_options=None, file=DESCRIPTOR,  create_key=_descriptor._internal_create_key),
    _descriptor.FieldDescriptor(
      name='dataset_id', full_name='training.TrainingJobMetadata.dataset_id', index=1,
      number=2, type=9, cpp_type=9, label=1,
      has_default_value=False, default_value=b"".decode('utf-8'),
      message_type=None, enum_type=None, containing_type=None,
      is_extension=False, extension_scope=None,
      serialized_options=None, file=DESCRIPTOR,  create_key=_descriptor._internal_create_key),
    _descriptor.FieldDescriptor(
      name='name', full_name='training.TrainingJobMetadata.name', index=2,
      number=3, type=9, cpp_type=9, label=1,
      has_default_value=False, default_value=b"".decode('utf-8'),
      message_type=None, enum_type=None, containing_type=None,
      is_extension=False, extension_scope=None,
      serialized_options=None, file=DESCRIPTOR,  create_key=_descriptor._internal_create_key),
    _descriptor.FieldDescriptor(
      name='train_data_version_hash', full_name='training.TrainingJobMetadata.train_data_version_hash', index=3,
      number=4, type=9, cpp_type=9, label=1,
      has_default_value=False, default_value=b"".decode('utf-8'),
      message_type=None, enum_type=None, containing_type=None,
      is_extension=False, extension_scope=None,
      serialized_options=None, file=DESCRIPTOR,  create_key=_descriptor._internal_create_key),
    _descriptor.FieldDescriptor(
      name='parameters', full_name='training.TrainingJobMetadata.parameters', index=4,
      number=5, type=11, cpp_type=10, label=3,
      has_default_value=False, default_value=[],
      message_type=None, enum_type=None, containing_type=None,
      is_extension=False, extension_scope=None,
      serialized_options=None, file=DESCRIPTOR,  create_key=_descriptor._internal_create_key),
  ],
  extensions=[
  ],
  nested_types=[_TRAININGJOBMETADATA_PARAMETERSENTRY, ],
  enum_types=[
  ],
  serialized_options=None,
  is_extendable=False,
  syntax='proto3',
  extension_ranges=[],
  oneofs=[
  ],
  serialized_start=358,
  serialized_end=583,
)

_TRAINREQUEST.fields_by_name['metadata'].message_type = _TRAININGJOBMETADATA
_GETTRAININGSTATUSRESPONSE.fields_by_name['status'].enum_type = _TRAININGSTATUS
_GETTRAININGSTATUSRESPONSE.fields_by_name['metadata'].message_type = _TRAININGJOBMETADATA
_TRAININGJOBMETADATA_PARAMETERSENTRY.containing_type = _TRAININGJOBMETADATA
_TRAININGJOBMETADATA.fields_by_name['parameters'].message_type = _TRAININGJOBMETADATA_PARAMETERSENTRY
DESCRIPTOR.message_types_by_name['TrainRequest'] = _TRAINREQUEST
DESCRIPTOR.message_types_by_name['TrainResponse'] = _TRAINRESPONSE
DESCRIPTOR.message_types_by_name['GetTrainingStatusRequest'] = _GETTRAININGSTATUSREQUEST
DESCRIPTOR.message_types_by_name['GetTrainingStatusResponse'] = _GETTRAININGSTATUSRESPONSE
DESCRIPTOR.message_types_by_name['TrainingJobMetadata'] = _TRAININGJOBMETADATA
DESCRIPTOR.enum_types_by_name['TrainingStatus'] = _TRAININGSTATUS
_sym_db.RegisterFileDescriptor(DESCRIPTOR)

TrainRequest = _reflection.GeneratedProtocolMessageType('TrainRequest', (_message.Message,), {
  'DESCRIPTOR' : _TRAINREQUEST,
  '__module__' : 'training_service_pb2'
  # @@protoc_insertion_point(class_scope:training.TrainRequest)
  })
_sym_db.RegisterMessage(TrainRequest)

TrainResponse = _reflection.GeneratedProtocolMessageType('TrainResponse', (_message.Message,), {
  'DESCRIPTOR' : _TRAINRESPONSE,
  '__module__' : 'training_service_pb2'
  # @@protoc_insertion_point(class_scope:training.TrainResponse)
  })
_sym_db.RegisterMessage(TrainResponse)

GetTrainingStatusRequest = _reflection.GeneratedProtocolMessageType('GetTrainingStatusRequest', (_message.Message,), {
  'DESCRIPTOR' : _GETTRAININGSTATUSREQUEST,
  '__module__' : 'training_service_pb2'
  # @@protoc_insertion_point(class_scope:training.GetTrainingStatusRequest)
  })
_sym_db.RegisterMessage(GetTrainingStatusRequest)

GetTrainingStatusResponse = _reflection.GeneratedProtocolMessageType('GetTrainingStatusResponse', (_message.Message,), {
  'DESCRIPTOR' : _GETTRAININGSTATUSRESPONSE,
  '__module__' : 'training_service_pb2'
  # @@protoc_insertion_point(class_scope:training.GetTrainingStatusResponse)
  })
_sym_db.RegisterMessage(GetTrainingStatusResponse)

TrainingJobMetadata = _reflection.GeneratedProtocolMessageType('TrainingJobMetadata', (_message.Message,), {

  'ParametersEntry' : _reflection.GeneratedProtocolMessageType('ParametersEntry', (_message.Message,), {
    'DESCRIPTOR' : _TRAININGJOBMETADATA_PARAMETERSENTRY,
    '__module__' : 'training_service_pb2'
    # @@protoc_insertion_point(class_scope:training.TrainingJobMetadata.ParametersEntry)
    })
  ,
  'DESCRIPTOR' : _TRAININGJOBMETADATA,
  '__module__' : 'training_service_pb2'
  # @@protoc_insertion_point(class_scope:training.TrainingJobMetadata)
  })
_sym_db.RegisterMessage(TrainingJobMetadata)
_sym_db.RegisterMessage(TrainingJobMetadata.ParametersEntry)


DESCRIPTOR._options = None
_TRAININGJOBMETADATA_PARAMETERSENTRY._options = None

_TRAININGSERVICE = _descriptor.ServiceDescriptor(
  name='TrainingService',
  full_name='training.TrainingService',
  file=DESCRIPTOR,
  index=0,
  serialized_options=None,
  create_key=_descriptor._internal_create_key,
  serialized_start=668,
  serialized_end=837,
  methods=[
  _descriptor.MethodDescriptor(
    name='Train',
    full_name='training.TrainingService.Train',
    index=0,
    containing_service=None,
    input_type=_TRAINREQUEST,
    output_type=_TRAINRESPONSE,
    serialized_options=None,
    create_key=_descriptor._internal_create_key,
  ),
  _descriptor.MethodDescriptor(
    name='GetTrainingStatus',
    full_name='training.TrainingService.GetTrainingStatus',
    index=1,
    containing_service=None,
    input_type=_GETTRAININGSTATUSREQUEST,
    output_type=_GETTRAININGSTATUSRESPONSE,
    serialized_options=None,
    create_key=_descriptor._internal_create_key,
  ),
])
_sym_db.RegisterServiceDescriptor(_TRAININGSERVICE)

DESCRIPTOR.services_by_name['TrainingService'] = _TRAININGSERVICE

# @@protoc_insertion_point(module_scope)
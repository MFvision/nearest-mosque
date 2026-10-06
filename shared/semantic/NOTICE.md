# Search model

`model.bin` is derived from **static-similarity-mrl-multilingual-v1** by sentence-transformers
(https://huggingface.co/sentence-transformers/static-similarity-mrl-multilingual-v1), licensed under the
Apache License, Version 2.0 (https://www.apache.org/licenses/LICENSE-2.0). Its tokenizer vocabulary is that
of BERT multilingual uncased (Google, Apache-2.0).

Changes: only tokens written in Latin or Arabic script are kept (63,031 of 105,879), only the first 256 of
1,024 dimensions, and each row is stored as int8 with a float32 scale. `tools/semantic.py --build-model`
reproduces the file from the original `model.safetensors` and `tokenizer.json` (retrieved 2026-10-06).

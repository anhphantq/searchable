## Import Vietnamese Analysis 7.17.1

Tải file .jar tại đây:

    https://github.com/duydo/elasticsearch-analysis-vietnamese/releases/tag/v7.17.1

Đồng thời cài đặt các dependency tại file pom.xml

## Build C++ tokenizer for Vietnamese library

    git clone https://github.com/coccoc/coccoc-tokenizer.git
    cd coccoc-tokenizer && mkdir build && cd build
    cmake -DBUILD_JAVA=1 ..
    make install

By default, the make install installs:

- the lib commands (tokenizer, dict_compiler and vn_lang_tool) under /usr/local/bin
- the dynamic lib (libcoccoc_tokenizer_jni.so) under /usr/local/lib/. The plugin uses this lib directly.
- the dictionary files under /usr/local/share/tokenizer/dicts. The plugin uses this path for dict_path by default.

Refer the [repo](https://github.com/coccoc/coccoc-tokenizer) for more information to build the library.

Chạy LucenceWorker.java. Lưu ý: chạy các container RabbitMQ, DB, sender trước.

Tra một số lỗi thường gặp tại [đây](https://github.com/duydo/elasticsearch-analysis-vietnamese)
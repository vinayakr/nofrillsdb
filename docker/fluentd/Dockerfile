FROM fluent/fluentd:edge
USER root

RUN gem install fluent-plugin-sumologic_output --no-document
RUN gem install fluent-plugin-rewrite-tag-filter --no-document

USER fluent
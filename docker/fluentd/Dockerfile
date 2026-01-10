FROM fluent/fluentd:edge
USER root

# Install Sumo Logic plugin
RUN gem install fluent-plugin-sumologic_output --no-document

# Install Rewrite Tag Filter plugin
RUN gem install fluent-plugin-rewrite-tag-filter --no-document

# Copy configuration file
COPY fluent.conf /fluentd/etc/

# Change ownership to fluent user
RUN chown fluent:fluent /fluentd/etc/fluent.conf 

# Switch back to fluent user
USER fluent

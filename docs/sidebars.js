const sidebars = {
  sidebar: [
    "index",
    "getting-started",
    "explanation/amqp-basics",
    "explanation/delivery-and-failure",
    {
      type: "category",
      label: "Reference",
      collapsed: false,
      items: [
        "reference/amqp",
        "reference/channel",
        "reference/model"
      ]
    },
    "tutorial-work-queue",
    {
      type: "category",
      label: "How-to Guides",
      collapsed: true,
      items: [
        "how-to-connect",
        "how-to-connect-with-tls",
        "how-to-acknowledge-messages",
        "how-to-limit-prefetch",
        "how-to-process-messages-in-parallel",
        "how-to-route-with-exchanges",
        "how-to-publish-durable-messages",
        "how-to-dead-letter-and-expire-messages",
        "how-to-stop-a-consumer",
        "how-to-recover-from-a-closed-channel",
        "how-to-test-with-testcontainers"
      ]
    },
    "resources"
  ]
};

module.exports = sidebars;

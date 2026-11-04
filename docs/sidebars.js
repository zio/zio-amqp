const sidebars = {
  sidebar: [
    {
      type: "category",
      label: "ZIO AMQP",
      collapsed: false,
      link: { type: "doc", id: "index" },
      items: [
        "tutorial-work-queue",
        {
          type: "category",
          label: "How-to Guides",
          items: [
            "how-to-connect",
            "how-to-acknowledge-messages",
            "how-to-limit-prefetch",
            "how-to-route-with-exchanges",
            "how-to-publish-durable-messages",
            "how-to-stop-a-consumer",
            "how-to-test-with-testcontainers"
          ]
        },
        {
          type: "category",
          label: "Reference",
          items: [
            "reference/amqp",
            "reference/channel",
            "reference/model"
          ]
        }
      ]
    }
  ]
};

module.exports = sidebars;

import { config } from "@vue/test-utils";

const slotStub = { template: "<div><slot /></div>" };

config.global.stubs = {
  teleport: true,
  "van-nav-bar": {
    props: ["title", "leftArrow", "rightText"],
    emits: ["click-left", "click-right"],
    template: `
      <div class="nav-bar-stub">
        <button v-if="leftArrow" type="button" data-testid="nav-back" @click="$emit('click-left')" />
        <span>{{ title }}</span>
        <slot name="right" />
      </div>
    `,
  },
  "van-tabbar": slotStub,
  "van-tabbar-item": slotStub,
  "van-sticky": slotStub,
  "van-tabs": slotStub,
  "van-tab": slotStub,
  "van-list": slotStub,
  "van-pull-refresh": slotStub,
};

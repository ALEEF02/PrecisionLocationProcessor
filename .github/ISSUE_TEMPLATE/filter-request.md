---
name: Filter request
about: Suggest a new filter
title: "[Filter Req]"
labels: ''
assignees: ''

---

**Please describe the filter in detail**
 - A clear and elaborative description of what the filter should do
 - What should the default setting should be (minimum, maximum, is exactly)

**Can this filter create a bounded area without prior locations?**
Examples of yes:
 - Bounding box or ellipse can create a bounded region from the user's requirements
 - An isochrone (equal time) plot from a single geographical location can create a bounded region.
Examples of no:
 - Light Pollution *cannot* create a bounded region, as defining a min/max sky quality meter (SQM) would operate over the whole globe without a prior region bounded
 - [ ] Yes
 - [ ] No

**Detail the data source for the filter**
Links and documentation must be provided. Any API key source must be entirely free.

**Additional context**
Add any other context or markups about the filter design here.
